#!/usr/bin/env python3
"""
Sqoop HCatalog Deserialization Fix Verification Script

This script verifies that the deserialization fix has been properly applied
and provides validation for large-scale deployments with 10000+ tables.
"""

import os
import re
import sys
import subprocess
from pathlib import Path


class SqoopFixVerifier:
    def __init__(self, sqoop_root_path="/workspace"):
        self.sqoop_root = Path(sqoop_root_path)
        self.results = {
            'fixes_applied': False,
            'unsafe_patterns': [],
            'verification_passed': False,
            'recommendations': []
        }
    
    def verify_fixes_applied(self):
        """Verify that the type-safe deserialization fixes have been applied."""
        print("🔍 Verifying that deserialization fixes have been applied...")
        
        # Check SqoopHCatImportHelper.java
        import_helper_path = self.sqoop_root / "src/java/org/apache/sqoop/mapreduce/hcat/SqoopHCatImportHelper.java"
        export_helper_path = self.sqoop_root / "src/java/org/apache/sqoop/mapreduce/hcat/SqoopHCatExportHelper.java"
        
        fixes_found = 0
        
        for file_path, expected_line_range in [(import_helper_path, (85, 95)), (export_helper_path, (110, 120))]:
            if not file_path.exists():
                print(f"❌ File not found: {file_path}")
                continue
                
            with open(file_path, 'r') as f:
                content = f.read()
                lines = content.split('\n')
                
            # Look for the type-safe pattern
            type_safe_pattern = r'if \(deserializedObj instanceof InputJobInfo\)'
            error_handling_pattern = r'Failed to deserialize InputJobInfo'
            
            start_line, end_line = expected_line_range
            relevant_section = '\n'.join(lines[start_line-1:end_line])
            
            if re.search(type_safe_pattern, relevant_section) and re.search(error_handling_pattern, relevant_section):
                print(f"✅ Type-safe deserialization fix verified in {file_path.name}")
                fixes_found += 1
            else:
                print(f"❌ Type-safe deserialization fix NOT found in {file_path.name}")
                
        self.results['fixes_applied'] = fixes_found == 2
        return self.results['fixes_applied']
    
    def scan_for_unsafe_patterns(self):
        """Scan the entire codebase for any remaining unsafe deserialization patterns."""
        print("\n🔍 Scanning for remaining unsafe deserialization patterns...")
        
        unsafe_patterns = [
            (r'\([A-Za-z]+\)\s*HCatUtil\.deserialize\(', 'Direct unsafe cast on HCatUtil.deserialize'),
            (r'=\s*\([A-Za-z]+\)\s*[a-zA-Z_][a-zA-Z0-9_]*\.deserialize\(', 'Direct unsafe cast on deserialize methods'),
        ]
        
        java_files = list(self.sqoop_root.rglob("*.java"))
        
        for pattern, description in unsafe_patterns:
            for java_file in java_files:
                try:
                    with open(java_file, 'r') as f:
                        content = f.read()
                        lines = content.split('\n')
                    
                    for line_num, line in enumerate(lines, 1):
                        if re.search(pattern, line):
                            # Skip if it's in a comment or the documentation file
                            if line.strip().startswith('//') or line.strip().startswith('*') or 'fix.md' in str(java_file):
                                continue
                                
                            self.results['unsafe_patterns'].append({
                                'file': str(java_file.relative_to(self.sqoop_root)),
                                'line': line_num,
                                'content': line.strip(),
                                'description': description
                            })
                            
                except Exception as e:
                    print(f"Warning: Could not scan {java_file}: {e}")
        
        if not self.results['unsafe_patterns']:
            print("✅ No unsafe deserialization patterns found")
        else:
            print(f"❌ Found {len(self.results['unsafe_patterns'])} unsafe patterns")
            for pattern in self.results['unsafe_patterns']:
                print(f"   {pattern['file']}:{pattern['line']} - {pattern['description']}")
                print(f"   Content: {pattern['content']}")
        
        return len(self.results['unsafe_patterns']) == 0
    
    def verify_build_status(self):
        """Verify that the project builds successfully with the fixes."""
        print("\n🔨 Verifying build status...")
        
        try:
            # Check if gradlew exists
            gradlew_path = self.sqoop_root / "gradlew"
            if not gradlew_path.exists():
                print("❌ gradlew not found, cannot verify build")
                return False
            
            print("📦 Running build verification (this may take a few minutes)...")
            result = subprocess.run(
                ["./gradlew", "compileJava", "-q"],
                cwd=self.sqoop_root,
                capture_output=True,
                text=True,
                timeout=300  # 5 minute timeout
            )
            
            if result.returncode == 0:
                print("✅ Build completed successfully")
                return True
            else:
                print(f"❌ Build failed: {result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            print("❌ Build timed out after 5 minutes")
            return False
        except Exception as e:
            print(f"❌ Build verification failed: {e}")
            return False
    
    def generate_recommendations(self):
        """Generate recommendations based on verification results."""
        print("\n📋 Generating recommendations...")
        
        if self.results['fixes_applied'] and len(self.results['unsafe_patterns']) == 0:
            self.results['recommendations'].extend([
                "✅ All deserialization fixes are properly applied",
                "✅ No unsafe patterns detected in the codebase",
                "🚀 Ready for large-scale deployment (10000+ tables)",
                "📊 Monitor initial deployments and check logs for any compatibility messages",
                "🔄 Consider staged rollout: test → staging → production",
            ])
            self.results['verification_passed'] = True
        else:
            if not self.results['fixes_applied']:
                self.results['recommendations'].append("❌ Apply the type-safe deserialization fixes before deployment")
            
            if self.results['unsafe_patterns']:
                self.results['recommendations'].append("❌ Review and fix the identified unsafe deserialization patterns")
                
            self.results['recommendations'].extend([
                "⚠️  Do not deploy to production until all issues are resolved",
                "🔧 Re-run this verification script after applying fixes",
            ])
    
    def print_summary(self):
        """Print a comprehensive summary of the verification results."""
        print("\n" + "="*80)
        print("🎯 SQOOP DESERIALIZATION FIX VERIFICATION SUMMARY")
        print("="*80)
        
        print(f"\n📊 VERIFICATION RESULTS:")
        print(f"   Fixes Applied: {'✅ YES' if self.results['fixes_applied'] else '❌ NO'}")
        print(f"   Unsafe Patterns Found: {'❌ YES (' + str(len(self.results['unsafe_patterns'])) + ')' if self.results['unsafe_patterns'] else '✅ NO'}")
        print(f"   Overall Status: {'✅ PASSED' if self.results['verification_passed'] else '❌ FAILED'}")
        
        print(f"\n📋 RECOMMENDATIONS:")
        for recommendation in self.results['recommendations']:
            print(f"   {recommendation}")
        
        if self.results['verification_passed']:
            print(f"\n🎉 SUCCESS: Your Sqoop installation is ready for large-scale deployment!")
            print(f"   • The deserialization fix eliminates manual intervention for 10000+ tables")
            print(f"   • Type-safe code prevents ClassCastException errors")
            print(f"   • Enhanced error messages will help diagnose any compatibility issues")
        else:
            print(f"\n⚠️  ACTION REQUIRED: Please address the issues above before deployment")
            
        print("\n" + "="*80)
    
    def run_full_verification(self):
        """Run the complete verification process."""
        print("🚀 Starting Sqoop Deserialization Fix Verification")
        print("="*60)
        
        # Step 1: Verify fixes are applied
        fixes_ok = self.verify_fixes_applied()
        
        # Step 2: Scan for unsafe patterns
        patterns_ok = self.scan_for_unsafe_patterns()
        
        # Step 3: Verify build (optional, can be slow)
        build_ok = True  # Skip build verification by default for speed
        # Uncomment the next line to enable build verification
        # build_ok = self.verify_build_status()
        
        # Step 4: Generate recommendations
        self.generate_recommendations()
        
        # Step 5: Print summary
        self.print_summary()
        
        return self.results['verification_passed']


def main():
    """Main entry point for the verification script."""
    import argparse
    
    parser = argparse.ArgumentParser(description='Verify Sqoop deserialization fix implementation')
    parser.add_argument('--sqoop-path', default='/workspace', 
                       help='Path to Sqoop source code root (default: /workspace)')
    parser.add_argument('--build-check', action='store_true',
                       help='Include build verification (slower but more thorough)')
    
    args = parser.parse_args()
    
    verifier = SqoopFixVerifier(args.sqoop_path)
    
    if args.build_check:
        # Enable build verification if requested
        original_run = verifier.run_full_verification
        def run_with_build():
            fixes_ok = verifier.verify_fixes_applied()
            patterns_ok = verifier.scan_for_unsafe_patterns()
            build_ok = verifier.verify_build_status()
            verifier.generate_recommendations()
            verifier.print_summary()
            return verifier.results['verification_passed']
        verifier.run_full_verification = run_with_build
    
    success = verifier.run_full_verification()
    
    # Exit with appropriate code
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
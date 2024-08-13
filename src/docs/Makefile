#
# Copyright 2011 The Apache Software Foundation
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

BUILDROOT=../../build
BUILD_DIR=$(BUILDROOT)/docs

VERSION=Unknown

# Building documentation under CentOS causes xmlto to have issues
# with validation. Disable this step.
VALIDATION=$(shell ( lsb_release -d | grep CentOS > /dev/null 2>&1 ) \
       && echo "--skip-validation" )

all: man userguide devguide supportfiles index

userguide: $(BUILD_DIR)/SqoopUserGuide.html

devguide: $(BUILD_DIR)/SqoopDevGuide.html

index: $(BUILD_DIR)/index.html

MANSOURCES=$(shell echo man/sqoop*.txt)
MANPAGES=$(MANSOURCES:.txt=.1.gz)

REVDATE=$(shell date)


website: 
	mkdir -p $(WEBSITE_DIR)

	asciidoctor --safe -a revnumber=$(VERSION) \
                -b html5 \
                -o $(WEBSITE_DIR)/SqoopUserGuide.html \
                user/SqoopUserGuide.txt

	asciidoctor --safe -a revnumber=$(VERSION) \
                -b html5 \
                -o $(WEBSITE_DIR)/SqoopDevGuide.html \
                dev/SqoopDevGuide.txt

	asciidoctor --safe -a revnumber=$(VERSION) \
                -b html5 \
                -o $(WEBSITE_DIR)/index.html \
                web/index.txt

	cp web/docbook.css $(WEBSITE_DIR)
	rsync -a web/images $(WEBSITE_DIR)/

man: $(MANPAGES)

%.1.gz : %.txt
	mkdir -p man
	asciidoctor --backend=manpage -a author="Sqoop Team" -o man/$*.1 $<
	gzip $*.1
	mkdir -p $(BUILD_DIR)
	mv $@ $(BUILD_DIR)

$(BUILD_DIR)/SqoopUserGuide.html: user/SqoopUserGuide.txt
	mkdir -p $(BUILD_DIR)
	asciidoctor --backend=html5 -a revnumber=$(VERSION) \
		-o $(BUILD_DIR)/SqoopUserGuide.html \
		user/SqoopUserGuide.txt

$(BUILD_DIR)/SqoopDevGuide.html: dev/SqoopDevGuide.txt
	mkdir -p $(BUILD_DIR)
	asciidoctor --backend=html5 -a revnumber=$(VERSION) \
		-o $(BUILD_DIR)/SqoopDevGuide.html \
		dev/SqoopDevGuide.txt

$(BUILD_DIR)/index.html: web/index.txt
	mkdir -p $(BUILD_DIR)
	asciidoctor --backend=html5 -a revnumber=$(VERSION) \
		-o $(BUILD_DIR)/index.html \
		web/index.txt

supportfiles:
	mkdir -p $(BUILD_DIR)
	cp web/docbook.css $(BUILD_DIR)
	rsync -a web/images $(BUILD_DIR)/

clean:
	-rm $(BUILD_DIR)/sqoop-*.1.gz
	-rm $(BUILD_DIR)/SqoopDevGuide.html
	-rm $(BUILD_DIR)/SqoopUserGuide.html
	-rm user/SqoopUserGuide.html
	-rm dev/SqoopDevGuide.html

.PHONY: all man userguide devguide clean supportfiles index


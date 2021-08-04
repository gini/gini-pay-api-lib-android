#! /bin/bash

DOC_DIR=ginipaylib/src/doc
BUILD_DIR=ginipaylib/build

mkdir -p $BUILD_DIR/integration-guide
cp -r $DOC_DIR/* $BUILD_DIR/integration-guide/
cd $BUILD_DIR/integration-guide
virtualenv ./virtualenv
source ./virtualenv/bin/activate
pip install -r requirements.txt
make html singlehtml
mkdir -p ../docs/
cp -r build/html/* ../docs/
deactivate
cd ..
rm -rf integration-guide/

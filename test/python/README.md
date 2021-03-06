# Python tests

Tests are written using unittest.

Run tests on Python 3.6.

Python path must include the streamsx code either by:

 * Setting environment variable `PYTHONPATH` to the absolute path of `streamsx.topology/com.ibm.streamsx.topology/opt/python/packages`.
 * Install `streamsx` package (to test the released package).


## Test locations

 * `topology` - Tests of the Python application api
 * `spl/tests` - Tests of SPL Primitive operators.
 * `scripts` - Tests of scripts installed by the pip package `streamsx`
 * `rest` - Python REST API bindings for Streams & Streaming Analytics service.

## Running tests with unittest

Change directory to the test directory and run something like:

```
cd test/python/topology

# All tests
python -u -m unittest discover

# A single test module
python -u -m unittest test2_spl

# A single test class
python -u -m unittest test2_spl.TestSPL

# A single test 
python -u -m unittest test2_spl.TestSPL.test_map_attr
```

### Running distributed test

Uses the python package of this repository, run `ant all` in repository root prior testing.

```
cd test/python
ant test.distributed
```

## Running tests with nose

Running with nose produces xunit (for Jenkins) and coverage reports.

Need to pip install `nose` and `nose-cov`.

Use the `*.cfg` files in `test/python`.

 * `nose.cfg` - the setup to run the tests
 * `all_tests.cfg` - All Python tests supported under nose.

```
cd test/python

# All tests
nosetests --config nose.cfg --config all_tests.cfg

# Single test class
nosetests --config nose.cfg topology/test2_spl.py
```

## Restricting test destination

The tests are run in one of three destination environments:

 * standalone - Run standalone using a local Streams install set by environment variable `STREAMS_INSTALL`.
 * distributed - Run distributed using a local Streams install set by environment variable `STREAMS_INSTALL`. Environment variables `STREAMS_DOMAIN_ID` and `STREAMS_INSTANCE_ID` set the instance to use.
 * streaming analytics - Run distributed using Streaming Analytics service.  Environment variables `VCAP_SERVICES` and `STREAMING_ANALYTICS_SERVICE_NAME` set the service to use.

When the environment is not setup for a destination the tests will be skipped, thus one can run against a single destination by unsetting environment variables for the other destinations.

For example, only run standalone:

```
unset VCAP_SERVICES
unset STREAMS_DOMAIN_ID

cd test/python
nosetests --config nose.cfg --config all_tests.cfg
```

For example, only run tests with streaming analytics service (remote build):

```
unset STREAMS_INSTALL
unset STREAMS_DOMAIN_ID
unset STREAMS_INSTANCE_ID
nosetests --config nose.cfg --config all_tests.cfg
```



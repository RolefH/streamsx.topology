# coding=utf-8
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017

"""
Runtime tester functionality.

********
Overview
********

Module containing runtime functionality for
:py:mod:`streamsx.topology.tester`.

When test is executed any specified :py:class:`Condition` instances
are executed in the context of the application under test (and
not the ``unittest`` class instance). This module separates out
the runtime execution code from the test definition module
:py:mod:`~streamsx.topology.tester`.

"""

from future.builtins import *

import streamsx.ec as ec
import streamsx.topology.context as stc
import streamsx.spl.op
import os
import unittest
import logging
import collections
import threading
import time

# the base class of Condition
class BaseCondition(object):
    def __init__(self, name=None):
        self.name = name

class Condition(BaseCondition):
    """A condition for testing.

    Args:
        name(str): Condition name, must be unique within the tester.
    """
    _METRIC_PREFIX = "streamsx.condition:"

    @staticmethod
    def _mn(mt, name):
        return Condition._METRIC_PREFIX + mt + ":" + name

    def __init__(self, name=None):
        super(Condition, self).__init__(name)

    def create(self, stream):
        cond_sink = stream.for_each(self, self.name)
        cond_sink.colocate(stream)
        cond_sink.category = 'Tester'
        cond_sink._op()._layout(hidden=True)

    @property
    def valid(self):
        """Is the condition valid.

        A subclass must set `valid` when the condition becomes valid.
        """
        return self._valid
    @valid.setter
    def valid(self, v):
        if self._fail:
           return
        if self._valid != v:
            if v:
                self._metric_valid.value = 1
            else:
                self._metric_valid.value = 0
            self._valid = v
        self._metric_seq += 1

    def fail(self):
        """Fail the condition.

        Marks the condition as failed. Once a condition has failed it
        can never become valid, the test that uses the condition will fail.
        """
        self._metric_fail.value = 1
        self.valid = False
        self._fail = True
        if (ec.is_standalone()):
            raise AssertionError("PythonCondition failed:" + str(self))

    def __getstate__(self):
        # Remove metrics from saved state.
        state = self.__dict__.copy()
        to_be_deleted = []
        for key in state:
            if key.startswith('_metric'):
                to_be_deleted.append(key)
        for key in to_be_deleted:
            del state[key]
        return state

    def __setstate__(self, state):
        self.__dict__.update(state)

    def __enter__(self):
        self._metric_valid = self._create_metric("valid", kind='Gauge')
        self._metric_seq = self._create_metric("seq")
        self._metric_fail = self._create_metric("fail", kind='Gauge')
        if self._starts_valid:
            self.valid = True

    def __exit__(self, exc_type, exc_value, traceback):
        if (ec.is_standalone()):
            if not self._fail and not self.valid:
                raise AssertionError("PythonCondition failed:" + str(self))

    def _create_metric(self, mt, kind=None):
        return ec.CustomMetric(self, name=Condition._mn(mt, self.name), kind=kind)

class _TupleExactCount(PythonCondition):
    def __init__(self, target, name=None):
        super(_TupleExactCount, self).__init__(name)
        self.target = target
        self.count = 0
        self._starts_valid = target == 0

    def __call__(self, tuple):
        self.count += 1
        self.valid = self.target == self.count
        if self.count > self.target:
            self.fail()

    def __str__(self):
        return "Exact tuple count: expected:" + str(self.target) + " received:" + str(self.count)

class _TupleAtLeastCount(PythonCondition):
    def __init__(self, target, name=None):
        super(_TupleAtLeastCount, self).__init__(name)
        self.target = target
        self.count = 0
        self._starts_valid = target == 0

    def __call__(self, tuple):
        self.count += 1
        self.valid = self.count >= self.target

    def __str__(self):
        return "At least tuple count: expected:" + str(self.target) + " received:" + str(self.count)

class _StreamContents(PythonCondition):
    def __init__(self, expected, name=None):
        super(_StreamContents, self).__init__(name)
        self.expected = expected
        self.received = []

    def __call__(self, tuple):
        self.received.append(tuple)
        if len(self.received) > len(self.expected):
            self.fail()
            return

        if self._check_for_failure():
            return

        self.valid = len(self.received) == len(self.expected)

    def _check_for_failure(self):
        """Check for failure.
        """
        if self.expected[len(self.received) - 1] != self.received[-1]:
            self.fail()
            return True
        return False

    def __str__(self):
        return "Stream contents: expected:" + str(self.expected) + " received:" + str(self.received)

class _UnorderedStreamContents(_StreamContents):
    def _check_for_failure(self):
        """Unordered check for failure.

        Can only check when the expected number of tuples have been received.
        """
        if len(self.expected) == len(self.received):
            if collections.Counter(self.expected) != collections.Counter(self.received):
                self.fail()
                return True
        return False

class _TupleCheck(PythonCondition):
    def __init__(self, checker, name=None):
        super(_TupleCheck, self).__init__(name)
        self.checker = checker

    def __call__(self, tuple):
        if not self.checker(tuple):
            self.fail()
        else:
            # Will not override if already failed
            self.valid = True

    def __str__(self):
        return "Tuple checker:" + str(self.checker)


<<<<<<< HEAD
class _Resetter(Condition):
=======
class _Resetter(BaseCondition):
>>>>>>> 62d6d51... Consistent region resetter integrated with tester (needs cleanup)
    CONDITION_NAME = "ConditionRegionResetter"

    def __init__(self, topology, minimumResets=10):
        super(_Resetter, self).__init__(self.CONDITION_NAME)
        self.topology = topology
        self.minimumResets = minimumResets
        
<<<<<<< HEAD
    def attach(self, stream):
=======
    def create(self, stream):
>>>>>>> 62d6d51... Consistent region resetter integrated with tester (needs cleanup)
        params = {'minimumResets': self.minimumResets, 'conditionName': self.CONDITION_NAME}
        resetter = streamsx.spl.op.Invoke(self.topology, "com.ibm.streamsx.topology.testing.consistent::Resetter", inputs=None, schemas=None, params=params, name="ConsistentRegionResetter")
        

<<<<<<< HEAD
class _RunFor(PythonCondition):
=======
class _RunFor(Condition):
>>>>>>> 62d6d51... Consistent region resetter integrated with tester (needs cleanup)
    def __init__(self, duration):
        super(_RunFor, self).__init__("TestRunTime")
        self.duration = duration

    def __iter__(self):
        start = time.time()
        while True:
            time.sleep(1)
            if (time.time() - start) >= self.duration:
                self.valid = True
                return
            self.valid = False
            yield None

    def __str__(self):
        return "Tuple run time:" + str(self.duration)

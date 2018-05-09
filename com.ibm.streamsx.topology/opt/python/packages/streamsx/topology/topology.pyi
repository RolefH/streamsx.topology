# coding=utf-8
# Licensed Materials - Property of IBM
# Copyright IBM Corp. 2017
from typing import Any, Callable, Iterable, List, Set, Union

from enum import Enum
import datetime

from streamsx.topology.schema import _AnySchema
import streamsx.types

_StreamWindow = Union[Stream, Window]

class Routing(Enum):
    ROUND_ROBIN=1
    KEY_PARTITIONED=2
    HASH_PARTITIONED=3


class SubscribeConnection(Enum):
    Direct = 0
    Buffered = 1


class Topology(object):
    def __init__(self, name: str=None, namespace: str=None, files: Any=None) -> None: ...
    def source(self, func : Union[Callable[[], Any],Iterable[Any]], name: str =None) -> Stream: ...
    def name(self) -> str: ...
    def namespace(self) -> str: ...
    def subscribe(self, topic: str, schema: _AnySchema=None, name: str=None, connect: SubscribeConnection=None, buffer_capacity: int=None, buffer_full_policy: streamsx.types.CongestionPolicy=None) -> Stream: ...
    def add_file_dependency(self, path: str, location: str) -> str: ...
    def add_pip_package(self, requirement: str) -> None: ...
    def create_submission_parameter(self, name:str, default=None, type_=None): ...
    def checkpoint_period(self, period: int, uints: TimeUnit) -> None: ...


class Stream(object):
    def name(self) -> str: ...
    def for_each(self, func: Callable[[Any],None], name: str=None) -> 'Sink': ...
    def sink(self, func: Callable[[Any], None], name: str=None) -> 'Sink': ...
    def filter(self, func: Callable[[Any], bool], name: str=None) -> 'Stream': ...
    def view(self, buffer_time: float=10.0, sample_size: int=10000, name: str=None, description: str=None, start: bool=True) -> View: ...
    def map(self, func: Callable[[Any], Any], name: Any=None, schema: _AnySchema=None) -> 'Stream': ...
    def transform(self, func: Callable[[Any], Any], name: str=None) -> 'Stream': ...
    def flat_map(self, func: Any, name: str=None) -> 'Stream': ...
    def multi_transform(self, func: Any, name: str=None) -> 'Stream': ...
    def isolate(self) -> 'Stream': ...
    def low_latency(self) -> 'Stream': ...
    def end_low_latency(self) -> 'Stream': ...
    def parallel(self, width: Any, routing: Routing=Routing.ROUND_ROBIN, func: Any=None, name: str=None) -> 'Stream': ...
    def set_parallel(self, width: int) -> 'Stream': ...
    def end_parallel(self) -> 'Stream': ...
    def last(self, size: int) -> Window: ...
    def union(self, streamSet: Set['Stream']) -> 'Stream': ...
    def print(self, tag: str=None, name: str=None) -> 'Sink': ...
    def publish(self, topic: str, schema: Any=None, name: str=None) -> 'Sink': ...
    def autonomous(self) -> 'Stream': ...
    def as_string(self, name: str=None) -> 'Stream': ...
    def as_json(self, force_object: Any=bool, name: str=None) -> 'Stream': ...
    def resource_tags(self) -> Any: ...


class View(object):
    def initialize_rest(self) -> None: ...
    def stop_data_fetch(self) -> None: ...
    def start_data_fetch(self) -> Any: ...

class PendingStream(object):
    def __init__(self, topology: Topology) -> None: ...
    @property
    def stream(self) -> Stream: ...
    def complete(self, stream: Stream) -> None: ...
    def is_complete(self) -> bool: ...

class Sink(object): ...

class Window(object):
    def trigger(self, when: Union[int,datetime.timedelta]=1) -> 'Window': ...
    def aggregate(self, function: Callable[[List[Any]], Any], name: str=None, schema: _AnySchema=None) -> 'Stream': ...


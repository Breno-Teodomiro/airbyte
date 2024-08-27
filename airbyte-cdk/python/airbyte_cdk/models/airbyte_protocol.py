#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#

from dataclasses import InitVar, dataclass
from typing import Annotated, Any, Dict, List, Mapping, Optional

from airbyte_protocol_dataclasses.models import *
from serpyco_rs.metadata import Alias


@dataclass
class AirbyteStateBlob:
    """
    A dataclass that dynamically sets attributes based on provided keyword arguments and positional arguments.
    Used to "mimic" pydantic Basemodel with ConfigDict(extra='allow') option.

    The `AirbyteStateBlob` class allows for flexible instantiation by accepting any number of keyword arguments
    and positional arguments. These are used to dynamically update the instance's attributes. This class is useful
    in scenarios where the attributes of an object are not known until runtime and need to be set dynamically.

    Attributes:
        kwargs (InitVar[Mapping[str, Any]]): A dictionary of keyword arguments used to set attributes dynamically.

    Methods:
        __init__(*args: Any, **kwargs: Any) -> None:
            Initializes the `AirbyteStateBlob` by setting attributes from the provided arguments.

        __eq__(other: object) -> bool:
            Checks equality between two `AirbyteStateBlob` instances based on their internal dictionaries.
            Returns `False` if the other object is not an instance of `AirbyteStateBlob`.
    """
    kwargs: InitVar[Mapping[str, Any]]

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        # Set any attribute passed in through kwargs
        for arg in args:
            self.__dict__.update(arg)
        for key, value in kwargs.items():
            setattr(self, key, value)

    def __eq__(self, other: object) -> bool:
        return False if not isinstance(other, AirbyteStateBlob) else bool(self.__dict__ == other.__dict__)


# The following dataclasses have been redeclared to include the new version of AirbyteStateBlob
@dataclass
class AirbyteStreamState:
    stream_descriptor: StreamDescriptor
    stream_state: Optional[AirbyteStateBlob] = None


@dataclass
class AirbyteGlobalState:
    stream_states: List[AirbyteStreamState]
    shared_state: Optional[AirbyteStateBlob] = None


@dataclass
class AirbyteStateMessage:
    type: Optional[AirbyteStateType] = None
    stream: Optional[AirbyteStreamState] = None
    global_: Annotated[AirbyteGlobalState | None, Alias("global")] = None # "global" is a reserved keyword in python ⇒ Alias is used for (de-)serialization
    data: Optional[Dict[str, Any]] = None
    sourceStats: Optional[AirbyteStateStats] = None
    destinationStats: Optional[AirbyteStateStats] = None


@dataclass
class AirbyteMessage:
    type: Type
    log: Optional[AirbyteLogMessage] = None
    spec: Optional[ConnectorSpecification] = None
    connectionStatus: Optional[AirbyteConnectionStatus] = None
    catalog: Optional[AirbyteCatalog] = None
    record: Optional[AirbyteRecordMessage] = None
    state: Optional[AirbyteStateMessage] = None
    trace: Optional[AirbyteTraceMessage] = None
    control: Optional[AirbyteControlMessage] = None

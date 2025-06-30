# OPACA Demo Services Container

This package contains a simple demo- and test setup that can be used for demoing Reallabor-like services in OPACA.

Complementary to the real or dummy-fied Reallabor services, this module contains additional demo- and dummy-services that do not have a real-life counterpart, or work entirely independently. Currently, it contains the following agents and services:

* Desk Booking Agent: lists several (imaginary) desks in the rooms of ZEKI, which can be booked (permanently removing them from the list of available desks). This service is currently used in one of the examples for the OPACA BPMN editor.

* Servlet Agent: provides a simple website that can be accessed via an "extra port", as well as several actions for setting messages and table values to be shown on that website. Can be used as a stand-in for other services in demos that should have a real-world effect but are currently not available.

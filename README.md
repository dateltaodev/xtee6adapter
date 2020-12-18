# xtee6adapter

``xtee6adapter`` is a Java library for building X-Road v6 Adapter Servers. The library implements X-Road v6 [SOAP profile (version 4.0.16)](http://x-road.eu/docs/x-road_message_protocol_v4.0.pdf) and [Service Metadata Protocol (version 2.1.5)](http://x-road.eu/docs/x-road_service_metadata_protocol.pdf). The library takes care of serialization and deserialization of SOAP messages: built-in support for standard X-Road SOAP headers, only processing of application request and response elements from/to SOAP Body must be implemented.
Supports _Multipart/Related_ request and response.


### Documentation

The most essential classes of the library are:

* ``ee.datel.xtee.proxy.ProxyServlet`` : represents X-Road producer member that produces services to X-Road.
* ``ee.datel.xtee.proxy.WsdlServlet`` : represents X-Road producer member that produces services description (WSDL) to X-Road.
* ``ee.datel.xtee.proxy.response.ListMethods`` : represents X-Road producer member that produces metadata service _listMethods_ to X-Road.
* ``ee.datel.xtee.proxy.response.RestClient`` : client for service producer with REST (XML) interface.
* ``ee.datel.xtee.proxy.response.MtomClient`` : client for service producer with SOAP (MTOM) interface.
* ``ee.datel.xtee.proxy.server.logger.RequestLoggerFilter`` : logs all request/responses into file system.
* ``ee.datel.xtee.proxy.server.ServerConfiguration`` : reads and holds adapter configuration.

Client adds to each producer request _HTTP headers_:
* **Xtee-ClientMemberCode** - clientMemberCode from input _Header_.
* **Xtee-UserId** - userId from input _Header_.
* **Xtee-RequestId**- requestId from input _Header_.
* **Xtee-ThreadNumber** - request adapter's identificator - same used in request/responses log files name.
* **SoapAction** - {_subsystemCode_}.{_serviceCode_}.{_serviceVersion_}.**only for SOAP producers**.


### Build configuration

In pom.xml file for each profile:
* ``<conf.proxy.url>`` - this adapter access url, used in wsdl as _soap:address location="<conf.proxy.url>"_ 
* ``<application.configuration>`` - configuration zip file full path and filename (optional then on class path)
* ``<application.logging.pathbase>`` - full path for java logger and request/response files
* ``<application.logging.max-age-months>`` - how many months keep request/response files in _<application.logging.pathbase>_/requests/ 

###  Configuration ZIP file
#### xtee-proxy.properties

Addeded into ``listMethods`` response as xml commenet: 
* metadata.profile= # configuration profile name
* metadata.version= # configuration version identficator
* metadata.date= # configuration version timestamp

Service configuration, for each service:
* proxy.{_subsystemCode_}.{_serviceCode_}.mode= # rest or mtom - service producer type
* proxy.{_subsystemCode_}.{_serviceCode_}.version= # {_serviceVersion_} 
* proxy.{_subsystemCode_}.{_serviceCode_}.xmlns= # service namespace
* proxy.{_subsystemCode_}.{_serviceCode_}.url= # service producer URL. **For REST producers adapter appends /{_serviceCode_}/{_serviceVersion_}**.
* proxy.{_subsystemCode_}.{_serviceCode_}.title= # service title

#### Services description
* In zip file (or on class path ``/configuration/``)  for each ``subsystemCode`` own directory named as {subsystemCode}.
* In the directory for each ``serviceCode + serviceVersion`` own xsd file named as {_serviceCode_}.{_serviceVersion_}.xsd. XSD file describes service request and response elements.

### Service producer
##### REST example

> ```
@RequestMapping(value = "/archiveTables/v1", method = RequestMethod.POST, produces = MediaType.APPLICATION_XML_VALUE,
              consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
  @ResponseBody
  public ArchiveTablesResponse getArchiveTables(final HttpServletRequest request) throws ManagedException {
    ...
  }
```

##### SOAP example

> ```
@SoapAction("etak.downloadArchiveFeature.v1")
  @ResponsePayload
  public DownloadArchiveFeatureResponse downloadArchiveFeature(@RequestPayload final DownloadArchiveFeature request)
              throws Exception {
      ...
  }
```




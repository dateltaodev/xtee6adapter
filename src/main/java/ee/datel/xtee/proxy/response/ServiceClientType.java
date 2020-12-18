package ee.datel.xtee.proxy.response;

public enum ServiceClientType {
  xtee5(new Xtee5Client()), rest(new RestClient()), mtom(new MtomClient()), listMethods(new ListMethods());
  private final ServiceClient client;

  private ServiceClientType(final ServiceClient cl) {
    client = cl;
  }

  ServiceClient getClient() {
    return client;
  }

}

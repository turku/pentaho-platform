package org.pentaho.test.platform.web.http.api;

import static junit.framework.Assert.assertEquals;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pentaho.platform.api.engine.IOutputHandler;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.IPlatformPlugin;
import org.pentaho.platform.api.engine.IPluginManager;
import org.pentaho.platform.api.engine.IPluginProvider;
import org.pentaho.platform.api.engine.IPluginResourceLoader;
import org.pentaho.platform.api.engine.PlatformPluginRegistrationException;
import org.pentaho.platform.api.engine.IPentahoDefinableObjectFactory.Scope;
import org.pentaho.platform.api.repository.IContentItem;
import org.pentaho.platform.engine.core.solution.ContentGeneratorInfo;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.core.system.boot.PlatformInitializationException;
import org.pentaho.platform.engine.services.solution.BaseContentGenerator;
import org.pentaho.platform.plugin.services.pluginmgr.DefaultPluginManager;
import org.pentaho.platform.plugin.services.pluginmgr.PlatformPlugin;
import org.pentaho.platform.plugin.services.pluginmgr.PluginResourceLoader;
import org.pentaho.platform.web.http.filters.PentahoRequestContextFilter;
import org.pentaho.test.platform.engine.core.MicroPlatform;
import org.tuckey.web.filters.urlrewrite.RequestProxy;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.WebAppDescriptor;
import com.sun.jersey.test.framework.spi.container.grizzly.GrizzlyTestContainerFactory;

@SuppressWarnings("nls")
public class FilePerspectiveResourceTest extends JerseyTest {
  public static final String USERNAME_JOE = "joe";
  public static final String TENANT_ID_ACME = "acme";

  private static MicroPlatform mp = new MicroPlatform("test-res/FileOutputResourceTest/");

  private static MicroPlatform.RepositoryModule repo;

  private static WebAppDescriptor webAppDescriptor = new WebAppDescriptor.Builder(
      "org.pentaho.platform.web.http.api.resources").contextPath("api").addFilter(PentahoRequestContextFilter.class,
      "pentahoRequestContextFilter").build();

  public FilePerspectiveResourceTest() throws Exception {
    super(webAppDescriptor);
    this.setTestContainerFactory(new GrizzlyTestContainerFactory());
    mp.setFullyQualifiedServerUrl(getBaseURI() + webAppDescriptor.getContextPath() + "/");
    mp.define(IPluginManager.class, DefaultPluginManager.class, Scope.GLOBAL);
    mp.define(IPluginResourceLoader.class, PluginResourceLoader.class, Scope.GLOBAL);
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    BasicConfigurator.configure();
    Logger.getLogger(RequestProxy.class).setLevel(Level.DEBUG);
    repo = mp.getRepositoryModule();
    repo.up();
  }

  @AfterClass
  public static void afterClass() {
    repo.down();
  }

  @Before
  public void beforeTest() throws PlatformInitializationException {
    repo.login(USERNAME_JOE, TENANT_ID_ACME);
  }

  @After
  public void afterTest() {
    repo.logout();
  }
  
  protected void createTestFile(String path, String text) {
    WebResource webResource = resource();
    ClientResponse postResponse = webResource.path(path).type(MediaType.TEXT_PLAIN).put(
        ClientResponse.class, text);
    assertEquals(ClientResponse.Status.OK, postResponse.getClientResponseStatus());
  }

  @Test
  public void testRenderThroughContentGenerator() throws PlatformInitializationException {
    mp.define(IPluginProvider.class, JUnitContentGeneratorPluginProvider.class);
    PentahoSystem.get(IPluginManager.class).reload();

    WebResource webResource = resource();

    //write a .junit file
    final String text = "sometext";
    createTestFile("repo/files/public:test.junit", text);

    //get the output of the .junit file (should invoke the content generator)
    String textResponse = webResource.path("repos/:public:test.junit/myperspective").get(String.class);
    assertEquals("Content generator failed to provide correct output", "hello viewer content generator", textResponse);
  }

  public static class JUnitContentGeneratorPluginProvider implements IPluginProvider {
    public List<IPlatformPlugin> getPlugins(IPentahoSession session) throws PlatformPluginRegistrationException {
      PlatformPlugin p = new PlatformPlugin();
      p.setId("JUnitContentGeneratorPluginProvider");

      ContentGeneratorInfo cg = new ContentGeneratorInfo();
      cg.setDescription("test plugin description");
      cg.setId("junit.myperspective");
      cg.setType("junit");
      cg.setTitle("JUnit CG");
      cg.setUrl("/bogus");
      cg.setClassname(JUnitContentGenerator.class.getName());
      p.addContentGenerator(cg);

      return Arrays.asList((IPlatformPlugin) p);
    }
  }

  @SuppressWarnings("serial")
  public static class JUnitContentGenerator extends BaseContentGenerator {
    @Override
    public void createContent() throws Exception {
      try {
      IContentItem responseContentItem = outputHandler.getOutputContentItem(IOutputHandler.RESPONSE,
          IOutputHandler.CONTENT, null, null, null);
      //mime type setting will blow up since servlet api used by grizzly is too old
      try {
        responseContentItem.setMimeType("text/plain");
      } catch (Throwable t) {}
      OutputStream outputStream = responseContentItem.getOutputStream(null);
      IOUtils.write("hello viewer content generator", outputStream);
      outputStream.close();
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    @Override
    public Log getLogger() {
      return null;
    }
  }
}
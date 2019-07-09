package com.ibm.streamsx.rest.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ibm.streamsx.rest.internal.ZipStream;

import com.ibm.streamsx.rest.StreamsConnection;
import com.ibm.streamsx.rest.Instance;
import com.ibm.streamsx.rest.Toolkit;

public class ToolkitAPITest {
  protected StreamsConnection connection;
  String instanceName;
  Instance instance;
  String testType;

  @BeforeClass
  public static void maybeSkip() {
    assumeTrue(null == System.getenv("STREAMS_DOMAIN_ID"));
  }

  @Before
  public void setup() throws Exception {
    setupConnection();
    deleteToolkits();
  }

  @After
  public void deleteToolkits() throws Exception {
    Set<String> deleteNames = new HashSet();
    deleteNames.add(gamesToolkitName);
    deleteNames.add(cardsToolkitName);
    deleteNames.add(bingoToolkitName);

    List<Toolkit> deleteToolkits;
    do {

      List<Toolkit> toolkits = connection.getToolkits();
      deleteToolkits = toolkits.stream()
        .filter(tk -> deleteNames.contains(tk.getName()))
        .collect(Collectors.toList());
      
      for(Toolkit toolkit: deleteToolkits){
        toolkit.delete();
      }

      Thread.sleep(5000);

    } while (!deleteToolkits.isEmpty());
  }

  @Test
  public void testGetToolkits() throws Exception {    
    List<Toolkit> toolkits = connection.getToolkits();
    
    // We don't know what toolkits are present on the host, but
    // we can assume at very least the standard spl toolkit is present.
    assertNotNull(toolkits);
    assertTrue(toolkits.size() > 0);
    assertNotNull(toolkits.get(0).getName());
    assertNotNull(toolkits.get(0).getVersion());

    // Find the spl toolkit (I think there can only be one)
    assertEquals(1, toolkits.stream()
                            .filter(tk -> tk.getName().equals("spl"))
                            .count());
    
    //for (Toolkit tk: toolkits) {
    //  System.out.println(tk.getName() + " " + tk.getVersion());
    //}
  }

  @Test
  public void testPostToolkit() throws Exception {
    Toolkit bingo = connection.putToolkit(bingo0Path);
    assertNotNull(bingo);
    assertEquals(bingo.getName(), bingoToolkitName);
    assertEquals(bingo.getVersion(), bingo0Version);
    assertEquals(bingo.getRequiredProductVersion(), "4.2");
    assertEquals(bingo.getResourceType(), "toolkit");

    // We don't know what the values the following attributes will have,
    // but we verify that the expected attributes do at least have values
    assertNotNull(bingo.getPath());

    // Verify that the new toolkit is in the list of all toolkits
    List<Toolkit> toolkits = connection.getToolkits();
    waitForToolkit(bingoToolkitName);

    assertTrue(bingo.delete());
  }

  @Test
  public void testDeleteToolkit() throws Exception {
    Toolkit bingo = connection.putToolkit(bingo0Path);
    assertNotNull(bingo);
    assertEquals(bingo.getName(), bingoToolkitName);
    assertEquals(bingo.getVersion(), bingo0Version);

    waitForToolkit(bingo.getName(), Optional.of(bingo.getVersion()));

    // deleting once should succeed.
    assertTrue(bingo.delete());
    
    // Verify that it has been removed
    assertToolkitNotExists(bingoToolkitName);

    // deleting again should fail.
    assertFalse(bingo.delete());

    // Post it again, then find it in the list of toolkits and delete
    // it from the Toolkit object from the list.
    bingo = connection.putToolkit(bingo0Path);
    assertNotNull(bingo);
    assertEquals(bingo.getName(), bingoToolkitName);
    assertEquals(bingo.getVersion(), bingo0Version);

    List<Toolkit> foundToolkits = findMatchingToolkits(bingoToolkitName, Optional.of(bingo0Version));
    assertEquals(foundToolkits.size(), 1);
    assertTrue(foundToolkits.get(0).delete());    
  }

  @Test
  public void testGetIndex() throws Exception {
    Toolkit bingo = connection.putToolkit(bingo0Path);
    assertNotNull(bingo);
    assertEquals(bingoToolkitName, bingo.getName());
    assertEquals(bingo0Version, bingo.getVersion());

    String index = bingo.getIndex();
    assertNotNull(index);

    // try parsing the xml, and verify some of the content.
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    assertTrue(factory.isNamespaceAware());
    DocumentBuilder builder = factory.newDocumentBuilder();
    InputStream is = new ByteArrayInputStream(index.getBytes("UTF-8"));
    Document doc = builder.parse(is);

    NodeList toolkitModelElementList = doc.getDocumentElement().getElementsByTagNameNS("http://www.ibm.com/xmlns/prod/streams/spl/toolkit", "toolkit");
    assertNotNull(toolkitModelElementList);
    assertEquals(1, toolkitModelElementList.getLength());

    Node toolkitElementNode = toolkitModelElementList.item(0);
    assertTrue(toolkitElementNode.hasAttributes());
    NamedNodeMap attributes = toolkitElementNode.getAttributes();
    assertNotNull(attributes);
    Node nameNode = attributes.getNamedItem("name");
    assertNotNull(nameNode);
    assertEquals(bingoToolkitName, nameNode.getNodeValue());
    Node versionNode = attributes.getNamedItem("version");
    assertNotNull(versionNode);
    assertEquals(bingo0Version, versionNode.getNodeValue());    

    assertTrue(bingo.delete());
  }

  // Test posting different versions of a toolkit.  Posting a version
  // equal to one that is currently deployed should fail,
  // but posting a different version should succeed.
  @Test
  public void testPostMultipleVersions() throws Exception {
    List<Toolkit> toolkits = connection.getToolkits();
   
    // First post version 1.0.1
    Toolkit bingo1 = connection.putToolkit(bingo1Path);
    assertNotNull(bingo1);
    waitForToolkit(bingoToolkitName, Optional.of(bingo1Version));

    // Post version 1.0.1 again.  It should return Null
    assertNull(connection.putToolkit(bingo1Path));

    // Post verison 1.0.0.  It should succeed as it does not match any
    // existing version.
    Toolkit bingo0 = connection.putToolkit(bingo0Path);
    assertNotNull(bingo0);
    waitForToolkit(bingoToolkitName, Optional.of(bingo0Version));

    // Version 1.0.1 should still exist
    assertToolkitExists(bingoToolkitName, Optional.of(bingo1Version));
    assertToolkitNotExists(bingoToolkitName, Optional.of(bingo2Version));

    // Post version 1.0.2.  All three version continue to exist.
    Toolkit bingo2 = connection.putToolkit(bingo2Path);
    assertNotNull(bingo2);
    waitForToolkit(bingoToolkitName, Optional.of(bingo2Version));
    
    assertToolkitExists(bingoToolkitName, Optional.of(bingo0Version));
    assertToolkitExists(bingoToolkitName, Optional.of(bingo1Version));

    assertTrue(bingo0.delete());
    assertTrue(bingo1.delete());
    assertTrue(bingo2.delete());
  }

  // Test getting the dependencies of a toolkit.
  @Test
  public void testGetDependencies() throws Exception {
    // Games depends on both cards and bingo.

    Toolkit bingo = connection.putToolkit(bingo0Path);
    assertNotNull(bingo);

    Toolkit cards = connection.putToolkit(cardsPath);
    assertNotNull(cards);
    
    Toolkit games = connection.putToolkit(gamesPath);
    assertNotNull(games);

    // bingo and cards have no dependencies
    assertEquals(0, bingo.getDependencies().size());
    assertEquals(0, cards.getDependencies().size());

    List<Toolkit.Dependency> gamesDependencies = games.getDependencies();
    assertEquals(2, gamesDependencies.size());

    assertEquals("com.example.bingo", gamesDependencies.get(0).getName());
    assertEquals("[1.0.0,2.0.0)", gamesDependencies.get(0).getVersion());
    assertEquals("com.example.cards", gamesDependencies.get(1).getName());
    assertEquals("[1.0.0,1.1.0)", gamesDependencies.get(1).getVersion());

    assertTrue(games.delete());
    assertTrue(cards.delete());
    assertTrue(bingo.delete());
  }

  // Test posting from a bad path
  @Test
  public void testBadPath() throws IOException {
    // Path does not exist
    File notExists = new File(bingo0Path.getParent(), "fleegle_tk");
    try {
      connection.putToolkit(notExists);
      fail("IOException expected");
    }
    catch(IllegalArgumentException e) {
      e.printStackTrace();
    }

    // Path is an individual file
    File toolkitXml = new File(bingo0Path, "toolkit.xml");
    try {
      connection.putToolkit(toolkitXml);
      fail("IOException expected");
    }
    catch(IllegalArgumentException e) {
      e.printStackTrace();
    }

    // Path is malformed garbage
    File garbagePath = new File("./toolkits/bingo_tk0\000/snork");
    try {
      connection.putToolkit(garbagePath);
      fail("IllegalArgumentException expected");
    }
    catch(IllegalArgumentException e) {
      e.printStackTrace();
    }

    // Not a toolkit directory.
    File notToolkit = bingo0Path.getParentFile();
    try {
      connection.putToolkit(notToolkit);
      fail("IOException expected");
    }
    catch(IllegalArgumentException e) {
      e.printStackTrace();
    }
  }

  // Test the zip file creation class.  Zip a directory, then write it to a file,
  // unzip it, and compare it to the original directory.
  @Test
  public void testZip() throws Exception {
    Path tkpath = bingo0Path.toPath();
    File baseDir = new File(System.getProperty("java.io.tmpdir"));
    Path targetParent = Files.createTempDirectory(baseDir.toPath(), "tz");
    try {
      Path target = new File(targetParent.toFile(),"tk.zip").toPath();
      try (InputStream is = ZipStream.fromPath(tkpath)) {
        Files.copy(is, target);
      }

      // Unzip the zip file with system tools and compare with the source
      // directory.  
      StringBuilder command = new StringBuilder();
      command.append("cd '").append(targetParent.toFile().getAbsolutePath()).append("' && ");
      command.append("unzip -qq tk.zip && ");
      command.append("diff -r bingo_tk0 '").append(bingo0Path.getAbsolutePath()).append("'");
      System.out.println(command);

      ProcessBuilder builder = new ProcessBuilder("bash","-c",command.toString());
      Process process = builder.start();
      int exitCode = process.waitFor();
    
      copy (process.getInputStream(), System.out);
      copy (process.getErrorStream(), System.err);

      assertEquals(0, exitCode);

    }
    finally {
      // Delete the temp files and contents this when test is done.  
      Files.walkFileTree(targetParent, new FileDeleter());
    }
  }

  static private void copy(InputStream is, OutputStream os) throws IOException {
    byte[] block = new byte[8192];
    int count = 0;
    while (count != -1) {
      os.write(block, 0, count);
      count = is.read(block);
    }
  }

  protected void setupConnection() throws Exception {
    if (connection == null) {
      testType = "DISTRIBUTED";
      
      instanceName = System.getenv("STREAMS_INSTANCE_ID");
      if (instanceName != null)
	System.out.println("InstanceName: " + instanceName);
      else
	System.out.println("InstanceName: assuming single instance");
      
      
      // TODO we need to change the way we get the URL for Cloud
      // pak for data.
      String buildUrl = System.getenv("STREAMS_BUILD_URL");
      assertNotNull("set STREAMS_BUILD_URL to run this test", buildUrl);
      System.out.println("build URL: " + buildUrl);
      connection = StreamsConnection.createInstance(null, null, null, buildUrl);
      
      if (!sslVerify())
	connection.allowInsecureHosts(true);
    }
  }

  public static boolean sslVerify() {
    String v = System.getProperty("topology.test.SSLVerify");
    if (v == null)
      return true;
    
    return Boolean.valueOf(v);
  }
  
  private List<Toolkit> findMatchingToolkits(String name, Optional<String> version) throws IOException {
    List<Toolkit> matches = connection.getToolkits().stream()
      .filter(tk -> (tk.getName().equals(name) && 
                     (!version.isPresent() || tk.getVersion().equals(version.get()))))
      .collect(Collectors.toList());
    return matches;
  }

  private void waitForToolkit(String name) throws Exception {
    waitForToolkit(name, Optional.empty());
  }

  private void waitForToolkit(String name, Optional<String> version) throws Exception {
    waitForToolkit(name, version, 60);
  }

  private void waitForToolkit(String name, Optional<String> version, int retries) throws Exception {
    int retry = 0;
    boolean found = false;
    while (!found && retry < retries) {
      List<Toolkit> matches = findMatchingToolkits(name, version);
      if (matches.isEmpty()) {
        Thread.sleep(1000);
        ++retry;
      }
      else {
        found = true;
      }
    }

    if (!found) {
      fail("Toolkit " + name + " not found");
    }
  }

  private void assertToolkitExists(String name) throws IOException {
    assertToolkitExists(name, Optional.empty());
  }

  private void assertToolkitExists(String name, Optional<String> version) throws IOException {
    List<Toolkit> matches = findMatchingToolkits(name, version);
    assertTrue (matches.size() >= 1);
  }

  private void assertToolkitNotExists(String name) throws IOException {
    assertToolkitNotExists(name, Optional.empty());
  }

  private void assertToolkitNotExists(String name, Optional<String> version) throws IOException {
    List<Toolkit> matches = findMatchingToolkits(name, version);
    assertEquals(0, matches.size());
  }

  private static class FileDeleter extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      Files.delete(file);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
      if (e == null) {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
      else {
        throw e;
      }
    }
  }

  static {
    // We can't really use a relative path here, because it is relative
    // to the location where the test is run.
    String toolkitDir = System.getProperty("topology.test.toolkit.dir", "../python/rest/toolkits/");
    bingo0Path = new File(toolkitDir, "bingo_tk0");
    bingo1Path = new File(toolkitDir, "bingo_tk1");
    bingo2Path = new File(toolkitDir, "bingo_tk2");
    cardsPath = new File(toolkitDir, "cards_tk");
    gamesPath = new File(toolkitDir, "games_tk");
  }

  private static final File bingo0Path;
  private static final File bingo1Path;
  private static final File bingo2Path;
  private static final File cardsPath;
  private static final File gamesPath;
  private static final String bingoToolkitName = "com.example.bingo";
  private static final String cardsToolkitName = "com.example.cards";
  private static final String gamesToolkitName = "com.example.games";
  private static final String bingo0Version = "1.0.0";
  private static final String bingo1Version = "1.0.1";
  private static final String bingo2Version = "1.0.2";
}

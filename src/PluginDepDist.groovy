import groovyx.net.http.RESTClient
import org.apache.http.entity.FileEntity

/**
 * @author Noam Y. Tenne
 */

println('### TeamCity Dependency Distributer ###')

def scriptProps = new ConfigSlurper().parse(new File('script.properties').toURL())
def pom = new XmlParser().parseText(new File(scriptProps.plugin.pom.path).text)
def tcVersion = pom.properties.'teamcity.version'.text()

println("Preparing dependencies for TC v${tcVersion}")
def tcDeps = pom.dependencyManagement.dependencies.dependency.findAll {
  it.groupId.text().contains('org.jetbrains.teamcity')
}
def m2Dir = new File("${System.properties['user.home']}/.m2/repository")
def tcInstallationDir = new File(scriptProps.tc.install.dir)

def restClient = new RESTClient(scriptProps.repo.url)
restClient.auth.basic scriptProps.deployer.username, scriptProps.deployer.password
restClient.encoder.'application/zip' = this.&encodeZipFile
def encodeZipFile(Object data) throws UnsupportedEncodingException {
  def entity = new FileEntity((File) data, "application/zip");
  entity.setContentType("application/zip");
  return entity
}

tcDeps.each {

  def pathBuilder = new StringBuilder()
  def groupId = it.groupId.text()
  def artifactId = it.artifactId.text()

  if ('org.jetbrains.teamcity.agent'.equals(groupId)) {
    pathBuilder.append('buildAgent/')
  } else if ('org.jetbrains.teamcity.webapp'.equals(groupId)) {
    pathBuilder.append('webapps/ROOT/WEB-INF/')
  }
  pathBuilder.append('lib/').append(artifactId).append('.jar')

  def dependencyJarFile = new File(tcInstallationDir, pathBuilder.toString())
  if (!dependencyJarFile.exists()) {
    throw new IllegalArgumentException("Unable to find TC dep: ${artifactId}");
  }
  def artifactPath = "${groupId.replace('.', '/')}/${artifactId}/${tcVersion}/${artifactId}-${tcVersion}.jar"
  def dependencyTarget = new File(m2Dir, artifactPath)
  dependencyTarget.getParentFile().mkdirs()
  dependencyTarget.createNewFile()
  def src = new FileInputStream(dependencyJarFile)
  def dst = new FileOutputStream(dependencyTarget)
  dst << src

  println("Copied TC dep to: ${dependencyTarget.getAbsolutePath()}")

  def response = restClient.put(path: artifactPath,
          body: dependencyJarFile,
          requestContentType: 'application/zip'
  )
  assert response.status == 201, response.data.text
  println("Deployed TC dep to: ${restClient.getUri().toString() + artifactPath}")
}

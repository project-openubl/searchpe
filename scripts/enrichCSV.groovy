@Grab('org.yaml:snakeyaml:1.33')
import org.yaml.snakeyaml.*
import groovy.yaml.*

def file = new File(this.args[0])
def version = this.args[1]

def fileReader = new FileReader(file)
def yaml = new Yaml().load(fileReader)

yaml.metadata.name = 'searchpe-operator.v' + version
yaml.spec.annotations.containerImage = 'quay.io/projectopenubl/searchpe-operator:v' + version
yaml.spec.install.spec.deployments[0].spec.template.spec.containers[0].image = 'quay.io/projectopenubl/searchpe-operator:v' + version
yaml.spec.version = version

DumperOptions options = new DumperOptions();
options.indent = 2
options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
options.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN

new Yaml(options).dump(yaml, new FileWriter(file))

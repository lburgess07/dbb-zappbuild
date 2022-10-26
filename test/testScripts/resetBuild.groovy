@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import com.ibm.dbb.*
import com.ibm.dbb.build.*
import com.ibm.jzos.ZFile

@Field BuildProperties props = BuildProperties.getInstance()
println "\n** Executing test script resetBuild.groovy"
argMap.testList.add("Reset Build") // Add test name to testList

// Get the DBB_HOME location
def dbbHome = EnvVars.getHome()
if (props.verbose) println "** DBB_HOME = ${dbbHome}"

// Create reset build command
def resetBuildCommand = [] 
resetBuildCommand << "${dbbHome}/bin/groovyz"
resetBuildCommand << "${props.zAppBuildDir}/build.groovy"
resetBuildCommand << "--workspace ${props.workspace}"
resetBuildCommand << "--application ${props.app}"
resetBuildCommand << (props.outDir ? "--outDir ${props.outDir}" : "--outDir ${props.zAppBuildDir}/out")
resetBuildCommand << "--hlq ${props.hlq}"
resetBuildCommand << "--logEncoding UTF-8"
resetBuildCommand << "--url ${props.url}"
resetBuildCommand << "--id ${props.id}"
resetBuildCommand << (props.pw ? "--pw ${props.pw}" : "--pwFile ${props.pwFile}")
resetBuildCommand << (props.verbose ? "--verbose" : "")
resetBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles}" : "")
resetBuildCommand << (props.propOverwrites ? "--propOverwrites ${props.propOverwrites}" : "")
resetBuildCommand << "--reset"

// Run reset build 
println "** Executing ${resetBuildCommand.join(" ")}"
def process = ['bash', '-c', resetBuildCommand.join(" ")].execute()
def outputStream = new StringBuffer();
process.waitForProcessOutput(outputStream, outputStream)

// Validate reset build
println "** Validating reset build"
// Validate clean reset build
try {
    assert outputStream.contains("Deleting collection") : "COLLECTION DELETION NOT FOUND IN OUTPUT"
    assert outputStream.contains("Deleting build result group") : "BUILD GROUP DELETION NOT FOUND IN OUTPUT"
    assert outputStream.contains("Build finished") : "RESET OF THE BUILD FAILED"

    argMap.testResults.add(["PASSED"])
    println "**"
    println "** RESET OF THE BUILD : PASSED **"
    println "**"
    
}
catch (AssertionError e) {
    def message = "!* FAILED: " + e.getMessage()
    argMap.testResults.add(message)
    println "\n$message"
    if (props.verbose) e.printStackTrace()
    println "OUTPUT STREAM: \n${outputStream}\n"
}


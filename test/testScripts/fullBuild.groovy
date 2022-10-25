@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import com.ibm.dbb.*
import com.ibm.dbb.build.*
import com.ibm.jzos.ZFile

@Field BuildProperties props = BuildProperties.getInstance()
println "\n** Executing test script fullBuild.groovy"
argMap.testList.add("Full Build") // Add test name to testList

// Get the DBB_HOME location
def dbbHome = EnvVars.getHome()
if (props.verbose) println "** DBB_HOME = ${dbbHome}"

// Create full build command
def fullBuildCommand = [] 
fullBuildCommand << "${dbbHome}/bin/groovyz"
fullBuildCommand << "${props.zAppBuildDir}/build.groovy"
fullBuildCommand << "--workspace ${props.workspace}"
fullBuildCommand << "--application ${props.app}"
fullBuildCommand << (props.outDir ? "--outDir ${props.outDir}" : "--outDir ${props.zAppBuildDir}/out")
fullBuildCommand << "--hlq ${props.hlq}"
fullBuildCommand << "--logEncoding UTF-8"
fullBuildCommand << "--url ${props.url}"
fullBuildCommand << "--id ${props.id}"
fullBuildCommand << (props.pw ? "--pw ${props.pw}" : "--pwFile ${props.pwFile}")
fullBuildCommand << (props.verbose ? "--verbose" : "")
fullBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles}" : "")
fullBuildCommand << "--fullBuild"

// Run full build 
println "** Executing ${fullBuildCommand.join(" ")}"
def process = ['bash', '-c', fullBuildCommand.join(" ")].execute()
def outputStream = new StringBuffer();
process.waitForProcessOutput(outputStream, outputStream)

//Validate build results
println "** Validating full build results"
def expectedFilesBuiltList = props.fullBuild_expectedFilesBuilt.split(',')

@Field def assertionList = []

try {
	// Validate clean build
	assert outputStream.contains("Build State : CLEAN") : "BUILD STATE NOT CLEAN"

	// Validate expected number of files built
	def numFullFiles = expectedFilesBuiltList.size()
	assert outputStream.contains("Total files processed : ${numFullFiles}") : "TOTAL FILES PROCESSED DOES NOT EQUAL EXPECTED '${numFullFiles}'"

	// Validate expected built files in output stream
	assert expectedFilesBuiltList.count{ i-> outputStream.contains(i) } == expectedFilesBuiltList.size() : "FILES PROCESSED DOES NOT MATCH EXPECTED '${expectedFilesBuiltList}'"
	
	println "**"
	println "** FULL BUILD TEST : PASSED **"
	println "**"
}
catch(AssertionError e) {
	def message = e.getMessage()
	props.testsSucceeded = false

	assertionList << message;
	println("!* FAILED FULL BUILD TEST: ${message}")
	if (props.verbose) e.printStackTrace()
	println "\n***"
	println "OUTPUT STREAM: \n${outputStream} \n"
	println "***"
}
finally {
	cleanUpDatasets()

	if (assertionList.size() == 0)
		argMap.testResults.add("PASSED")
	else
		argMap.testResults.add("!* FAILED: ${String.join(',', assertionList)}")
	
}

// script end

//*************************************************************
// Method Definitions
//*************************************************************

def cleanUpDatasets() {
	def segments = props.fullBuild_datasetsToCleanUp.split(',')
	
	println "Deleting full build PDSEs ${segments}"
	segments.each { segment ->
	    def pds = "'${props.hlq}.${segment}'"
	    if (ZFile.dsExists(pds)) {
	       if (props.verbose) println "** Deleting ${pds}"
	       ZFile.remove("//$pds")
	    }
	}
}

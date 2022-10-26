
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import com.ibm.dbb.*
import com.ibm.dbb.build.*
import com.ibm.jzos.ZFile

@Field BuildProperties props = BuildProperties.getInstance()
println "\n### Executing test script impactBuild_properties.groovy"
argMap.testList.add("Impact Build (Properties)") // add test to list

// Get the DBB_HOME location
def dbbHome = EnvVars.getHome()
if (props.verbose) println "** DBB_HOME = ${dbbHome}"

// Create full build command to initilize property dependencies
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
fullBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles},${props.zAppBuildDir}/test/applications/${props.app}/${props.impactBuild_properties_buildPropSetting}" : "")
fullBuildCommand << (props.propOverwrites ? "--propOverwrites ${props.propOverwrites}" : "")
fullBuildCommand << "--fullBuild"

// create impact build command
def impactBuildCommand = []
impactBuildCommand << "${dbbHome}/bin/groovyz"
impactBuildCommand << "${props.zAppBuildDir}/build.groovy"
impactBuildCommand << "--workspace ${props.workspace}"
impactBuildCommand << "--application ${props.app}"
impactBuildCommand << (props.outDir ? "--outDir ${props.outDir}" : "--outDir ${props.zAppBuildDir}/out")
impactBuildCommand << "--hlq ${props.hlq}"
impactBuildCommand << "--logEncoding UTF-8"
impactBuildCommand << "--url ${props.url}"
impactBuildCommand << "--id ${props.id}"
impactBuildCommand << (props.pw ? "--pw ${props.pw}" : "--pwFile ${props.pwFile}")
impactBuildCommand << (props.verbose ? "--verbose" : "")
impactBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles},${props.zAppBuildDir}/test/applications/${props.app}/${props.impactBuild_properties_buildPropSetting}" : "")
impactBuildCommand << (props.propOverwrites ? "--propOverwrites ${props.propOverwrites}" : "")
impactBuildCommand << "--impactBuild"

// iterate through change files to test impact build
@Field def assertionList = []
PropertyMappings filesBuiltMappings = new PropertyMappings('impactBuild_properties_expectedFilesBuilt')
def changedPropFile = props.impactBuild_properties_changedFile
println("** Processing changed files from impactBuild_properties_changedFiles property : ${changedPropFile}")
try {
		
		println "\n** Running build to set baseline"
				
		// run impact build
		println "** Executing ${fullBuildCommand.join(" ")}"
		def outputStream = new StringBuffer()
		def process = ['bash', '-c', fullBuildCommand.join(" ")].execute()
		process.waitForProcessOutput(outputStream, outputStream)
		
		
		println "\n** Running impact build test for changed file $changedPropFile"
		
		// update changed file in Git repo test branch
		copyAndCommit(changedPropFile)
		
		// run impact build
		println "** Executing ${impactBuildCommand.join(" ")}"
		outputStream = new StringBuffer()
		process = ['bash', '-c', impactBuildCommand.join(" ")].execute()
		process.waitForProcessOutput(outputStream, outputStream)
		
		// validate build results
		validateImpactBuild(changedPropFile, filesBuiltMappings, outputStream)
}
finally {
	cleanUpDatasets()

	if (assertionList.size() == 0)
		argMap.testResults.add(["PASSED"])
	else
		argMap.testResults.add(["!* FAILED: ", assertionList])
}
// script end

//*************************************************************
// Method Definitions
//*************************************************************

def copyAndCommit(String changedFile) {
	println "** Copying and committing ${props.zAppBuildDir}/test/applications/${props.app}/${changedFile} to ${props.appLocation}/${changedFile}"
	def commands = """
	cp ${props.zAppBuildDir}/test/applications/${props.app}/${changedFile} ${props.appLocation}/${changedFile}
	cd ${props.appLocation}/
	git add .
	git commit . -m "edited property file"
"""
	def task = ['bash', '-c', commands].execute()
	def outputStream = new StringBuffer();
	task.waitForProcessOutput(outputStream, outputStream)
}

def validateImpactBuild(String changedFile, PropertyMappings filesBuiltMappings, StringBuffer outputStream) {

	println "** Validating impact build results"
	def expectedFilesBuiltList = filesBuiltMappings.getValue(changedFile).split(',')
	
	try{
		// Validate clean build
		assert outputStream.contains("Build State : CLEAN") : "IMPACT BUILD STATE NOT CLEAN FOR CHANGED PROPERTY FILE $changedFile"

		// Validate expected number of files built
		def numImpactFiles = expectedFilesBuiltList.size()
		assert outputStream.contains("Total files processed : ${numImpactFiles}") : "TOTAL FILES PROCESSED ARE NOT EQUAL TO ${numImpactFiles} FOR CHANGED PROPERTY FILE $changedFile "

		// Validate expected built files in output stream
		assert expectedFilesBuiltList.count{ i-> outputStream.contains(i) } == expectedFilesBuiltList.size() : "LIST OF BUILT FILES DOES NOT MATCH EXPECTED LIST ${expectedFilesBuiltList} FOR CHANGED PROPERTY FILE $changedFile"
		

		println "**"
		println "** IMPACT BUILD ON PROPERTY CHANGE : PASSED FOR $changedFile **"
		println "**"
	}
	catch(AssertionError e) {
		def message = e.getMessage()
		props.testsSucceeded = "false"

		assertionList << message.substring(0, message.indexOf("Expression:"));
		println("\n!* FAILED IMPACT BUILD TEST - PROPERTIES: ${message}")
		if (props.verbose) e.printStackTrace()
		println "\n***"
		println "OUTPUT STREAM: \n${outputStream} \n"
		println "***"
 }
}
def cleanUpDatasets() {
	def segments = props.impactBuild_properties_datasetsToCleanUp.split(',')
	
	println "Deleting impact build PDSEs ${segments}"
	segments.each { segment ->
		def pds = "'${props.hlq}.${segment}'"
		if (ZFile.dsExists(pds)) {
		   if (props.verbose) println "** Deleting ${pds}"
		   ZFile.remove("//$pds")
		}
	}
}

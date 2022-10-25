
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import com.ibm.dbb.*
import com.ibm.dbb.build.*
import com.ibm.jzos.ZFile

@Field BuildProperties props = BuildProperties.getInstance()
println "\n** Executing test script impactBuild.groovy"
argMap.testList.add("Impact Build")

// Get the DBB_HOME location
def dbbHome = EnvVars.getHome()
if (props.verbose) println "** DBB_HOME = ${dbbHome}"

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
impactBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles}" : "")
impactBuildCommand << "--impactBuild"

// iterate through change files to test impact build
@Field def assertionList = []
PropertyMappings filesBuiltMappings = new PropertyMappings('impactBuild_expectedFilesBuilt')
def changedFiles = props.impactBuild_changedFiles.split(',')
println("** Processing changed files from impactBuild_changedFiles property : ${props.impactBuild_changedFiles}")
try {
	changedFiles.each { changedFile ->
		println "\n** Running impact build test for changed file $changedFile"
		
		// update changed file in Git repo test branch
		copyAndCommit(changedFile)
		
		// run impact build
		println "** Executing ${impactBuildCommand.join(" ")}"
		def outputStream = new StringBuffer()
		def process = ['bash', '-c', impactBuildCommand.join(" ")].execute()
		process.waitForProcessOutput(outputStream, outputStream)
		
		// validate build results
		validateImpactBuild(changedFile, filesBuiltMappings, outputStream)
	}
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

def copyAndCommit(String changedFile) {
	println "** Copying and committing ${props.zAppBuildDir}/test/applications/${props.app}/${changedFile} to ${props.appLocation}/${changedFile}"
	def commands = """
    cp ${props.zAppBuildDir}/test/applications/${props.app}/${changedFile} ${props.appLocation}/${changedFile}
    cd ${props.appLocation}/
    git add .
    git commit . -m "edited program file"
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
		assert outputStream.contains("Build State : CLEAN") : "IMPACT BUILD STATE NOT CLEAN FOR CHANGED FILE $changedFile"

		// Validate expected number of files built
		def numImpactFiles = expectedFilesBuiltList.size()
		assert outputStream.contains("Total files processed : ${numImpactFiles}") : "TOTAL FILES PROCESSED DOES NOT EQUAL EXPECTED '${numImpactFiles}' FOR CHANGED FILE $changedFile"

		// Validate expected built files in output stream
		assert expectedFilesBuiltList.count{ i-> outputStream.contains(i) } == expectedFilesBuiltList.size() : "LIST OF BUILT FILES DOES NOT MATCH EXPECTED '${expectedFilesBuiltList}' FOR CHANGED FILE $changedFile"
		
		println "**"
		println "** IMPACT BUILD TEST : PASSED FOR $changedFile **"
		println "**"
    }
    catch(AssertionError e) {
		def message = e.getMessage()
		props.testsSucceeded = false

		assertionList << message;
		println("\n!* FAILED IMPACT BUILD TEST - FILE DELETE: ${message}")
		if (props.verbose) e.printStackTrace()
		println "\n***"
		println "OUTPUT STREAM: \n${outputStream} \n"
		println "***"
 }
}
def cleanUpDatasets() {
	def segments = props.impactBuild_datasetsToCleanUp.split(',')
	
	println "Deleting impact build PDSEs ${segments}"
	segments.each { segment ->
	    def pds = "'${props.hlq}.${segment}'"
	    if (ZFile.dsExists(pds)) {
	       if (props.verbose) println "** Deleting ${pds}"
	       ZFile.remove("//$pds")
	    }
	}
}

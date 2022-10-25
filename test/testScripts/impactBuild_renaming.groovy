
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import com.ibm.dbb.*
import com.ibm.dbb.build.*
import com.ibm.jzos.ZFile

@Field BuildProperties props = BuildProperties.getInstance()
println "\n** Executing test script impactBuild_renaming.groovy"
argMap.testList.add("Impact Build (Renaming)") // add test to list

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
impactBuildCommand << "--verbose"
impactBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles}" : "")
impactBuildCommand << "--impactBuild"

// iterate through change files to test impact build
@Field def assertionList = []
PropertyMappings filesBuiltMappings = new PropertyMappings('impactBuild_rename_expectedFilesBuilt')

PropertyMappings renamedFilesMapping = new PropertyMappings('impactBuild_rename_renameFilesMapping')

def renameFiles = props.impactBuild_rename_renameFiles.split(',')
try {
	renameFiles.each{ renameFile ->
		
		newFilename=renamedFilesMapping.getValue(renameFile)

		// update changed file in Git repo test branch
		renameAndCommit(renameFile, newFilename)

		println "\n** Running impact after renaming file $renameFile to $newFilename"
				
		// run impact build
		println "** Executing ${impactBuildCommand.join(" ")}"
		def outputStream = new StringBuffer()
		def process = [
			'bash',
			'-c',
			impactBuildCommand.join(" ")
		].execute()
		process.waitForProcessOutput(outputStream, outputStream)

		// validate build results
		validateImpactBuild(renameFile, filesBuiltMappings, outputStream)
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

def renameAndCommit(String renameFile, String newFilename) {
	println "** Rename $renameFile to $newFilename"
	def commands = """
	mv ${props.appLocation}/${renameFile} ${props.appLocation}/${newFilename}
	git -C ${props.appLocation} add .
	git -C ${props.appLocation} commit . -m "renamed program file"
"""
	def task = ['bash', '-c', commands].execute()
	def outputStream = new StringBuffer();
	task.waitForProcessOutput(outputStream, outputStream)
}

def validateImpactBuild(String renameFile, PropertyMappings filesBuiltMappings, StringBuffer outputStream) {

	println "** Validating impact build results"
	def expectedFilesBuiltList = filesBuiltMappings.getValue(renameFile).split(',')

	try{
		// Validate clean build
		assert outputStream.contains("Build State : CLEAN") : "BUILD STATE NOT CLEAN FOR RENAMED FILE $renameFile"

		// Validate expected number of files built
		def numImpactFiles = expectedFilesBuiltList.size()
		assert outputStream.contains("Total files processed : ${numImpactFiles}") : "TOTAL FILES PROCESSED DOES NOT EQUAL EXCPECTED '${numImpactFiles}' FOR RENAMED FILE $renameFile"

		// Validate expected built files in output stream
		assert expectedFilesBuiltList.count{ i-> outputStream.contains(i) } == expectedFilesBuiltList.size() : "LIST OF BUILT FILES DOES NOT MATCH EXPECTED LIST '${expectedFilesBuiltList}' FOR RENAMED FILE $renameFile"

		// Validate message that file renamed was deleted from collections
		assert outputStream.contains("*** Deleting renamed logical file for ${props.app}/${renameFile}") : "DELETION OF LOGICAL FILE MISSING FROM VERBOSE OUTPUT FOR RENAMED FILE $renameFile"
		
		println "**"
		println "** IMPACT BUILD TEST - FILE RENAME : PASSED FOR RENAMING $renameFile **"
		println "**"
	}
	catch(AssertionError e) {
		def message = e.getMessage()
		props.testsSucceeded = false

		assertionList << message;
		println("!* FAILED IMPACT BUILD TEST - FILE RENAME: ${message}")
		if (props.verbose) e.printStackTrace()
		println "\n***"
		println "OUTPUT STREAM: \n${outputStream} \n"
		println "***"
	}
}
def cleanUpDatasets() {
	def segments = props.impactBuild_rename_datasetsToCleanUp.split(',')

	println "Deleting impact build PDSEs ${segments}"
	segments.each { segment ->
		def pds = "'${props.hlq}.${segment}'"
		if (ZFile.dsExists(pds)) {
			if (props.verbose) println "** Deleting ${pds}"
			ZFile.remove("//$pds")
		}
	}
}

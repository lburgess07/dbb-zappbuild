
@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import com.ibm.dbb.*
import com.ibm.dbb.build.*
import com.ibm.jzos.ZFile

@Field BuildProperties props = BuildProperties.getInstance()
println "\n** Executing test script impactBuild_deletion.groovy"
argMap.testList.add("Impact Build (Deletion)") // add test to list

// Get the DBB_HOME location
def dbbHome = EnvVars.getHome()
if (props.verbose) println "** DBB_HOME = ${dbbHome}"

// Create full build command to set baseline and populate output libraries
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
fullBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles},${props.zAppBuildDir}/test/applications/${props.app}/${props.impactBuild_deletion_buildPropSetting}" : "")
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
impactBuildCommand << "--verbose"
impactBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles},${props.zAppBuildDir}/test/applications/${props.app}/${props.impactBuild_deletion_buildPropSetting}" : "")
impactBuildCommand << "--impactBuild"

// iterate through change files to test impact build
@Field def assertionList = []

PropertyMappings outputsDeletedMappings = new PropertyMappings('impactBuild_deletion_deletedOutputs')


def deleteFiles = props.impactBuild_deletion_deleteFiles.split(',')
try {
	
	println "\n** Running full build to set baseline"

	// run impact build
	println "** Executing ${fullBuildCommand.join(" ")}"
	def outputStream = new StringBuffer()
	def process = [
		'bash',
		'-c',
		fullBuildCommand.join(" ")
	].execute()
	process.waitForProcessOutput(outputStream, outputStream)
	
	deleteFiles.each{ deleteFile ->
		
		// delete file in Git repo test branch
		deleteAndCommit(deleteFile)

		println "\n** Running impact after deleting file $deleteFile"
				
		// run impact build
		println "** Executing ${impactBuildCommand.join(" ")}"
		outputStream = new StringBuffer()
		process = [
			'bash',
			'-c',
			impactBuildCommand.join(" ")
		].execute()
		process.waitForProcessOutput(outputStream, outputStream)

		// validate build results
		validateImpactBuild(deleteFile, outputsDeletedMappings, outputStream)
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

def deleteAndCommit(String deleteFile) {
	println "** Delete $deleteFile"
	def commands = """
	rm ${props.appLocation}/${deleteFile}
	git -C ${props.appLocation} add .
	git -C ${props.appLocation} commit . -m "delete file"
"""
	def task = ['bash', '-c', commands].execute()
	def outputStream = new StringBuffer();
	task.waitForProcessOutput(outputStream, outputStream)
}

def validateImpactBuild(String deleteFile, PropertyMappings outputsDeletedMappings, StringBuffer outputStream) {

	println "** Validating impact build results"
	def expectedDeletedFilesList = outputsDeletedMappings.getValue(deleteFile).split(',')
	
	try{
		def memberName = CopyToPDS.createMemberName(deleteFile)
		
		
		// Validate clean build
		assert outputStream.contains("Build State : CLEAN") : "BUILD STATE NOT CLEAN FOR DELETED FILE $deleteFile"

		// Validate message that deleted file was deleted from collections
		assert outputStream.contains("*** Deleting logical file for ${props.app}/${deleteFile}") : "DID NOT FIND DELETION OF LOGICAL FILE IN OUTPUT FOR DELETED FILE $deleteFile"
		
		// Validate creation of the Delete Record 
		assert outputStream.contains("** Create deletion record for file") : "CREATION OF DELETION RECORD NOT FOUND IN OUTPUT FOR DELETED FILE $deleteFile "
		
		expectedDeletedFilesList.each { deletedOutput ->

			assert outputStream.contains("** Document deletion ${props.hlq}.${deletedOutput} for file") : "DOCUMENT DELETE RECORD CREATION NOT FOUND IN OUTPUT FOR DELETED FILE $deleteFile"

			// Validate deletion of output
			assert outputStream.contains("** Deleting ${props.hlq}.${deletedOutput}") : "DELETION OF LOAD MODULE NOT FOUND IN OUTPUT FOR DELETED FILE $deleteFile"

		}
		
		println "**"
		println "** IMPACT BUILD TEST - FILE DELETE : PASSED FOR DELETING $deleteFile **"
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
	def segments = props.impactBuild_deletion_datasetsToCleanUp.split(',')

	println "Deleting impact build PDSEs ${segments}"
	segments.each { segment ->
		def pds = "'${props.hlq}.${segment}'"
		if (ZFile.dsExists(pds)) {
			if (props.verbose) println "** Deleting ${pds}"
			ZFile.remove("//$pds")
		}
	}
}

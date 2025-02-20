# Build and install our ADU sample code.

# Environment variables that can be used to configure the behavior of this recipe.
# ADUC_GIT_URL          Changes the URL of github repository that ADU code is pulled from.
#                           Default: git://github.com/Azure/iot-hub-device-update
#
# ADUC_GIT_BRANCH       Changes the branch that ADU code is pulled from.
#                           Default: develop
#
# ADU_GIT_COMMIT        Changes to the commit from which to checkout the adu code.
#
# BUILD_TYPE            Changes the type of build produced by this recipe.
#                       Valid values are Debug, Release, RelWithDebInfo, and MinRelSize.
#                       These values are the same as the CMAKE_BUILD_TYPE variable.

LICENSE = "CLOSED"

# Defaults for Gen1
# These will not be set to the values seen here if these are already set
# in the environment's variables.
#

# # The agent generation. Can be "1" or "2"
ADU_GENERATION ?= "1"

# For gen1, the release come out of develop branch, not main.
ADU_GIT_BRANCH ?= "develop"
ADU_SRC_URI ?= "git://github.com/Azure/iot-hub-device-update"
SRC_URI ?= "${ADU_SRC_URI};protocol=https;branch=${ADU_GIT_BRANCH}"
ADU_GIT_COMMIT ?= "350a551dd9d3f5639eddceb75ef5b10e834865fe"
# CMake build types: "Release" "RelWithDebInfo" "MinSizeRel"
BUILD_TYPE ?= "Debug"
WITH_FEATURE_DELTA_UPDATE ?= "0"

# Handle override of default vars with those for Gen2
python() {
    try:
        adu_gen = d.getVar("ADU_GENERATION")
        if adu_gen != "1" && adu_gen != "2":
            bb.fatal(f"Invalid value '{adu_gen}' for ADU_GENERATION. Only '1' and '2' are allowed.")
        bb.note(f"Using adu_gen '{adu_gen}'")

        # Gen2 sample overrides only if the env vars are not already defined.
        if adu_gen == "2":
            git_url = "git://github.com/Azure/device-update"
            git_branch = "main"
            git_commit = "e9fad55211ce620e32de15e0c8d7b3525e447992"

            if not d.getVar("ADU_GIT_BRANCH", True):
                bb.note(f"  *** Setting ADU_GIT_BRANCH to: {git_branch}")
                d.setVar("ADU_GIT_BRANCH", git_branch)

            if not d.getVar("ADU_GIT_URL", True):
                bb.note(f"  *** Setting ADU_GIT_URL to: {git_url}")
                d.setVar("ADU_GIT_URL", git_url)

            if not d.getVar("ADU_GIT_COMMIT", True):
                bb.note(f"  *** Setting ADU_GIT_COMMIT to: {git_commit}")
                d.setVar("ADU_GIT_COMMIT", git_commit)

            #################################################
            # BEGIN - patch pre-do_configure to make finding libmosquitto work in du agent repo cmake:
            #
            # This is to bring in patch to add additional cmake function for finding libmosquitto to make it
            # work for yocto recipe.
            #
            # Add paths for custom files
            d.prependVar('FILESEXTRAPATHS', '${THISDIR}/files:')   # FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
            d.appendVar('SRC_URI', ' file://Findmosquitto.cmake')  # SRC_URI += "file://Findmosquitto.cmake"

            # Create configure_prepend function. The equivalent of:
            #
            # do_configure:prepend() {
            #    mkdir -p ${S}/cmake/modules/
            #    cp ${WORKDIR}/Findmosquitto.cmake ${S}/cmake/modules/
            # }
            #
            # Copy the find module during configure
            d.prependVar('do_configure', '''
            mkdir -p ${S}/cmake/modules/
            cp ${WORKDIR}/Findmosquitto.cmake ${S}/cmake/modules/
            ''')

            # Add extra CMake options
            # EXTRA_OECMAKE += "-DCMAKE_MODULE_PATH=${S}/cmake/modules"
            d.appendVar('EXTRA_OECMAKE', ' -DCMAKE_MODULE_PATH=${S}/cmake/modules')
            # END - patch to make finding libmosquitto work in du agent repo cmake:
            #################################################

    except Exception as ex:
        errorMessage = "Failed override of ADU_GIT_ vars based on ADU_GENERATION. An exception of type {0} occurred with message:\n{1} and Arguments:\n{2!r}".format(type(ex).__name__, str(ex), ex.args)
        bb.fatal(errorMessage)
}

SRCREV = "${ADU_GIT_COMMIT}"

PV = "1.1+git${SRCPV}"
S = "${WORKDIR}/git"

# DEPENDS are the build-time dependencies that must be built
# and available BEFORE the current recipe can be built.
#
# Common Build Dependencies are:
# curl, DO agent, and DO SDK
# Gen2 requires mosquitto recipe from openembedded meta-networking layer
DEPENDS = "deliveryoptimization-agent deliveryoptimization-sdk curl azure-iot-sdk-c"
DEPENDS += "${@bb.utils.contains('ADU_GENERATION', '1', 'azure-iot-sdk-c azure-sdk-for-cpp', '')}" # gen1-only
DEPENDS += "${@bb.utils.contains('ADU_GENERATION', '2', 'mosquitto', '')}"                         # gen2-only

# Append to the build-time dependencies as per differences between Gen1 and Gen2
#
# Generation 2 needs these to build:
#     mosquitto-dev          -->  for communicating to MQTT Brokers such as
#                                 Azure Event Grid or Mosquitto Server.
#
# Handling mosquitto-dev installation via install-deps.sh for now, but
# need to actually make it into recipe to ensure it's pinned to the correct
# version. Note: there is no runtime dep on mosquitto because mosquitto
# client is statically linked.
# d.appendVar('DEPENDS', ' mosquitto-dev')
#

inherit cmake useradd

#OpenSSL3.0 is not supported in all branches
# -- Ignore warnings for now...
TARGET_CFLAGS:append =   " -Wno-error=deprecated-declarations"
TARGET_CPPFLAGS:append = " -Wno-error=deprecated-declarations"
TARGET_CXXFLAGS:append = " -Wno-error=deprecated-declarations"

BUILD_TYPE ?= "Debug"
EXTRA_OECMAKE += "-DCMAKE_BUILD_TYPE=${BUILD_TYPE}"
# Don't treat warnings as errors.
EXTRA_OECMAKE += "-DADUC_WARNINGS_AS_ERRORS=OFF"
# Build the non-simulator (real) version of the client.
EXTRA_OECMAKE += "-DADUC_PLATFORM_LAYER=linux"
# Integrate with SWUpdate as the installer
EXTRA_OECMAKE += "-DADUC_CONTENT_HANDLERS=microsoft/swupdate"
# Set the path to the manufacturer file
EXTRA_OECMAKE += "-DADUC_MANUFACTURER_FILE=${sysconfdir}/adu-manufacturer"
# Set the path to the model file
EXTRA_OECMAKE += "-DADUC_MODEL_FILE=${sysconfdir}/adu-model"
# Set the path to the version file
EXTRA_OECMAKE += "-DADUC_VERSION_FILE=${sysconfdir}/adu-version"
# Use zlog as the logging library.
EXTRA_OECMAKE += "-DADUC_LOGGING_LIBRARY=zlog"
# Change the log directory.
EXTRA_OECMAKE += "-DADUC_LOG_FOLDER=/adu/logs"
# Use /adu directory for configuration.
# The /adu directory is on a seperate partition and is not updated during an OTA update.
EXTRA_OECMAKE += "-DADUC_CONF_FOLDER=/adu"
# Don't install/configure the daemon, another bitbake recipe will do that.
EXTRA_OECMAKE += "-DADUC_INSTALL_DAEMON=OFF"
#
# Using the installed DO SDK include files.
EXTRA_OECMAKE += "-DDOSDK_INCLUDE_DIR=${WORKDIR}/recipe-sysroot/usr/include"
EXTRA_OECMAKE += "-DCMAKE_VERBOSE_MAKEFILE:BOOL=ON"
# cpprest installs its config.cmake file in a non-standard location.
EXTRA_OECMAKE += "${@bb.utils.contains('ADU_GENERATION', '1', '-Dcpprestsdk_DIR=${WORKDIR}/recipe-sysroot/usr/lib/cmake', '', d)}"  # gen1-only

# RDEPENDS are the RUNTIME dependencies that must be installed on the target
# system for the cuurent package to function correctly.
#
# bash - for running shell scripts for script handler update payloads.
# swupdate - to install swupdate .swu swupdate v2 update payloads.
# adu-pub-key - to install public key for update package verification.
# adu-log-dir - to create the temporary log directory in the image.
# deliveryoptimization-agent-service - to install the delivery optimization agent for downloads.
# curl - for running the diagnostics component, curl content downloader
# azure-device-update-diffs - to include the recipe for github.com:azure/iot-hub-device-update-delta runtime shared lib for delta updates.
#
RDEPENDS:${PN} += "bash swupdate  adu-pub-key adu-log-dir deliveryoptimization-agent-service curl openssl-bin nss ca-certificates"
RDEPENDS:${PN} += "${@bb.utils.contains('WITH_FEATURE_DELTA_UPDATE', '1', 'azure-device-update-diffs', '', d)}"

ADUC_DATA_DIR ?= "/var/lib/adu"
ADUC_EXTENSIONS_DIR ?= "${ADUC_DATA_DIR}/extensions"
ADUC_EXTENSIONS_INSTALL_DIR ?= "${ADUC_EXTENSIONS_DIR}/sources"
ADUC_COMPONENT_ENUMERATOR_EXTENSION_DIR ?= "${ADUC_EXTENSIONS_DIR}/component_enumerator"
ADUC_CONTENT_DOWNLOADER_EXTENSION_DIR ?= "${ADUC_EXTENSIONS_DIR}/content_downloader"
ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR ?= "${ADUC_EXTENSIONS_DIR}/update_content_handlers"
ADUC_DOWNLOAD_HANDLER_EXTENSION_DIR ?= "${ADUC_EXTENSIONS_DIR}/download_handlers"
ADUC_DOWNLOADS_DIR ?= "${ADUC_DATA_DIR}/downloads"
ADUC_DOWNLOADS_FOLDER ?= "${ADUC_DOWNLOADS_DIR}"

ADUC_LOG_DIR ?= "/adu/logs"
ADUC_CONF_DIR ?= "/adu"

ADUUSER = "adu"
ADUGROUP = "adu"
DOUSER = "do"
DOGROUP = "do"

USERADD_PACKAGES = "${PN}"

GROUPADD_PARAM:${PN} = "\
    --gid 800 --system adu ; \
    --gid 801 --system do ; \
    "

# USERADD_PARAM specifies command line options to pass to the
# useradd command. Multiple users can be created by separating
# the commands with a semicolon.
# Here we'll create 'adu' user, and 'do' user.
# To download the update payload file, 'adu' user must be a member of 'do' group.
# To save downloaded file into 'adu' downloads directory, 'do' user must be a member of 'adu' group.
USERADD_PARAM:${PN} = "\
    --uid 800 --system -g ${ADUGROUP} -G ${DOGROUP} --no-create-home --shell /bin/false ${ADUUSER} ; \
    --uid 801 --system -g ${DOGROUP} -G ${ADUGROUP} --no-create-home --shell /bin/false ${DOUSER} ; \
    "

do_compile[depends] += "azure-iot-sdk-c:do_prepare_recipe_sysroot"
do_compile[depends] += "${@bb.utils.contains('ADU_GENERATION', '1', 'azure-sdk-for-cpp:do_prepare_recipe_sysroot', '')}"  # gen1-only

do_install:append() {
    #create ADUC_DATA_DIR
    install -d ${D}${ADUC_DATA_DIR}
    chgrp ${ADUGROUP} ${D}${ADUC_DATA_DIR}
    chown ${ADUUSER}:${ADUGROUP} ${D}${ADUC_DATA_DIR}
    chmod 0770 ${D}${ADUC_DATA_DIR}

    #create ADUC_EXTENSIONS_DIR
    install -d ${D}${ADUC_EXTENSIONS_DIR}
    chgrp ${ADUGROUP} ${D}${ADUC_EXTENSIONS_DIR}
    chmod 0770 ${D}${ADUC_EXTENSIONS_DIR}

    #create ADUC_EXTENSIONS_INSTALL_DIR
    install -d ${D}${ADUC_EXTENSIONS_INSTALL_DIR}
    chgrp ${ADUGROUP} ${D}${ADUC_EXTENSIONS_INSTALL_DIR}
    chmod 0770 ${D}${ADUC_EXTENSIONS_INSTALL_DIR}

    #create ADUC_COMPONENT_ENUMERATOR_EXTENSION_DIR
    install -d ${D}${ADUC_COMPONENT_ENUMERATOR_EXTENSION_DIR}
    chgrp ${ADUGROUP} ${D}${ADUC_COMPONENT_ENUMERATOR_EXTENSION_DIR}
    chmod 0770 ${D}${ADUC_COMPONENT_ENUMERATOR_EXTENSION_DIR}

    #create ADUC_CONTENT_DOWNLOADER_EXTENSION_DIR
    install -d ${D}${ADUC_CONTENT_DOWNLOADER_EXTENSION_DIR}
    chgrp ${ADUGROUP} ${D}${ADUC_CONTENT_DOWNLOADER_EXTENSION_DIR}
    chmod 0770 ${D}${ADUC_CONTENT_DOWNLOADER_EXTENSION_DIR}

    #create ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR
    install -d ${D}${ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR}
    chgrp ${ADUGROUP} ${D}${ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR}
    chmod 0770 ${D}${ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR}

    #create ADUC_DOWNLOADS_DIR
    install -d ${D}${ADUC_DOWNLOADS_DIR}
    chown ${ADUUSER}:${ADUGROUP} ${D}${ADUC_DOWNLOADS_DIR}
    chmod 0770 ${D}${ADUC_DOWNLOADS_DIR}

    #create ADUC_CONF_DIR
    install -d ${D}${ADUC_CONF_DIR}
    chown root:${ADUGROUP} ${D}${ADUC_CONF_DIR}
    chmod 0774 ${D}${ADUC_CONF_DIR}

    #create ADUC_LOG_DIR
    install -d ${D}${ADUC_LOG_DIR}
    chown ${ADUUSER}:${ADUGROUP} ${D}${ADUC_LOG_DIR}
    chmod 0774 ${D}${ADUC_LOG_DIR}

    install -m 0550 ${S}/src/adu-shell/scripts/adu-swupdate.sh ${D}${bindir}
    chown ${ADUUSER}:${ADUGROUP} ${D}${bindir}/adu-swupdate.sh

    #set owner for adu-shell
    chmod 0550 ${D}${bindir}/adu-shell
    chown root:${ADUGROUP} ${D}${bindir}/adu-shell

    #set S UID for adu-shell
    chmod u+s ${D}${bindir}/adu-shell
}

#We don't want the library file hashes to change between do_image -> do_package,
#otherwise the stored json hashes will be incorrect
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

#
# A helper function that registers the required agent's extensions.
#
fakeroot python do_registerAgentExtensions() {

    try:
        workDir = d.getVar("D")
        extensionInstallDir = d.getVar("ADUC_EXTENSIONS_INSTALL_DIR")
        updateContentRegistrationDirectory = d.getVar("ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR")
        contentDownloaderRegistrationDirectory = d.getVar("ADUC_CONTENT_DOWNLOADER_EXTENSION_DIR")
        downloadHandlerRegistrationDirectory = d.getVar("ADUC_DOWNLOAD_HANDLER_EXTENSION_DIR")

        register_content_handler("microsoft/swupdate:2", "{}/libmicrosoft_swupdate_2.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/update-manifest", "{}/libmicrosoft_steps_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/update-manifest:4", "{}/libmicrosoft_steps_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/update-manifest:5", "{}/libmicrosoft_steps_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/steps:1", "{}/libmicrosoft_steps_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/script:1", "{}/libmicrosoft_script_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        # TODO: re-enable DO content downloader once available again upstream
        #register_content_downloader("{}/libdeliveryoptimization_content_downloader.so".format(extensionInstallDir), contentDownloaderRegistrationDirectory, workDir)
        register_content_downloader("{}/libcurl_content_downloader.so".format(extensionInstallDir), contentDownloaderRegistrationDirectory, workDir)
        # TODO: re-enable delta here once patches for recipe is done
        #register_download_handler("microsoft/delta:1", "{}/libmicrosoft_delta_download_handler.so".format(extensionInstallDir), downloadHandlerRegistrationDirectory, workDir)

    except Exception as ex:
        errorMessage = "Failed to create DU Agent extension registration. An exception of type {0} occurred with message:\n{1} and Arguments:\n{2!r}".format(type(ex).__name__, str(ex), ex.args)
        bb.fatal(errorMessage)
}
do_registerAgentExtensions[depends] += "virtual/fakeroot-native:do_populate_sysroot"
addtask do_registerAgentExtensions after do_install before do_package

fakeroot do_registerAgentExtensions_permissions(){
    chown -R ${ADUUSER}:${ADUGROUP} ${D}${ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR}
}
do_registerAgentExtensions[depends] += "virtual/fakeroot-native:do_populate_sysroot"
addtask do_registerAgentExtensions_permissions after do_registerAgentExtensions before do_package

FILES:${PN} += "${bindir}/AducIotAgent"
FILES:${PN} += "${bindir}/adu-shell"
FILES:${PN} += "${bindir}/adu-swupdate.sh"
FILES:${PN} += "${ADUC_DATA_DIR}/* ${ADUC_LOG_DIR}/* ${ADUC_CONF_DIR}/*"
FILES:${PN} += "${ADUC_EXTENSIONS_DIR}/* ${ADUC_EXTENSIONS_INSTALL_DIR}/* ${ADUC_DOWNLOADS_DIR}/*"
FILES:${PN} += "${ADUC_COMPONENT_ENUMERATOR_EXTENSION_DIR}/* ${ADUC_CONTENT_DOWNLOADER_EXTENSION_DIR}/* ${ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR}/* ${ADUC_DOWNLOAD_HANDLER_EXTENSION_DIR}/*"

def create_handlerRegistration(handlerId, handlerFileInstallPath, handlerExtensionDir, handlerRegistrationFileName, workDir):
    import hashlib
    import os
    import io
    import base64
    import json

    registrationProperties = {"fileName":handlerFileInstallPath}
    handlerExtensionInstallDir = "{}{}".format(workDir, handlerExtensionDir)
    handlerFileWorkingPath = "{}{}".format(workDir, handlerFileInstallPath)
    handlerRegistrationOutputPath = os.path.join(handlerExtensionInstallDir, handlerRegistrationFileName)

    if not os.path.isfile(handlerFileWorkingPath):
        raise ValueError("Cannot generate ADU handler registration, the specified path does not exist: {}".format(handlerFileWorkingPath))

    # Get the file size
    registrationProperties["sizeInBytes"] = os.path.getsize(handlerFileWorkingPath)

    # Calculate the file hash
    with open(handlerFileWorkingPath, "rb") as handler:
        data = handler.read()
        sha256_hash = hashlib.sha256(data)
        base64Hash = base64.b64encode(sha256_hash.digest()).decode("ascii")
        registrationProperties["hashes"] = {"sha256":base64Hash}

    # Add the handler Id if provided
    if handlerId is not None:
        registrationProperties["handlerId"] = handlerId

    # Create any required directories and write the registration content to the registration file
    registrationContent = json.dumps(registrationProperties, indent=4)
    if not os.path.exists(handlerExtensionInstallDir):
        os.makedirs(handlerExtensionInstallDir)
    with open(handlerRegistrationOutputPath, "w") as registration:
        registration.write(registrationContent)

def register_content_handler(handlerId, handlerFileInstallPath, handlerExtensionDir, workDir):
    typedDirectoryName = handlerId.replace("/", "_").replace(":", "_")
    typedHandlerExtensionDir = os.path.join(handlerExtensionDir, typedDirectoryName)
    create_handlerRegistration(handlerId, handlerFileInstallPath, typedHandlerExtensionDir, "content_handler.json", workDir)

def register_content_downloader(handlerFileInstallPath, handlerExtensionDir, workDir):
    create_handlerRegistration(None, handlerFileInstallPath, handlerExtensionDir, "extension.json", workDir)

def register_download_handler(handlerId, handlerFileInstallPath, handlerExtensionDir, workDir):
    typedDirectoryName = handlerId.replace("/", "_").replace(":", "_")
    typedHandlerExtensionDir = os.path.join(handlerExtensionDir, typedDirectoryName)
    create_handlerRegistration(handlerId, handlerFileInstallPath, typedHandlerExtensionDir, "download_handler.json", workDir)

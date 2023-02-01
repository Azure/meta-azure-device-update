# Build and install our ADU sample code.

# Environment variables that can be used to configure the behavior of this recipe.
# ADUC_GIT_URL          Changes the URL of github repository that ADU code is pulled from.
#                           Default: git://github.com/Azure/iot-hub-device-update
#            
# ADUC_GIT_BRANCH       Changes the branch that ADU code is pulled from.
#                           Default: main
#
# BUILD_TYPE            Changes the type of build produced by this recipe.
#                       Valid values are Debug, Release, RelWithDebInfo, and MinRelSize.
#                       These values are the same as the CMAKE_BUILD_TYPE variable.

LICENSE = "CLOSED"

ADU_GIT_BRANCH ?= "main"

ADU_SRC_URI ?= "git://github.com/Azure/iot-hub-device-update"
SRC_URI = "${ADU_SRC_URI};protocol=https;branch=${ADU_GIT_BRANCH}"

ADU_GIT_COMMIT ?= "79ce3ba24c411d3b014226cd869e2b2d02159a20"
SRC_URI += "file://0001-Fixup-compilation-error.patch"

SRCREV = "${ADU_GIT_COMMIT}"

PV = "1.0+git${SRCPV}"
S = "${WORKDIR}/git" 

# ADUC depends on azure-iot-sdk-c, azure-blob-storage-file-upload-utility, DO Agent SDK, and curl
DEPENDS = "azure-iot-sdk-c azure-blob-storage-file-upload-utility deliveryoptimization-agent deliveryoptimization-sdk curl"

inherit cmake useradd

#OpenSSL3.0 is not yet supported in HEAD commit on main branch
# -- Ignore warnings for now...
TARGET_CFLAGS:append = " -Wno-error=deprecated-declarations"
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
# cpprest installs its config.cmake file in a non-standard location.
# Tell cmake where to find it.
EXTRA_OECMAKE += "-Dcpprestsdk_DIR=${WORKDIR}/recipe-sysroot/usr/lib/cmake"
# Using the installed DO SDK include files.
EXTRA_OECMAKE += "-DDOSDK_INCLUDE_DIR=${WORKDIR}/recipe-sysroot/usr/include"

EXTRA_OECMAKE += "-DCMAKE_VERBOSE_MAKEFILE:BOOL=ON"

# bash - for running shell scripts for install.
# swupdate - to install update package.
# adu-pub-key - to install public key for update package verification.
# adu-log-dir - to create the temporary log directory in the image.
# deliveryoptimization-agent-service - to install the delivery optimization agent for downloads.
# curl - for running the diagnostics component
RDEPENDS:${PN} += "bash swupdate  adu-pub-key adu-log-dir deliveryoptimization-agent-service azure-device-update-diffs curl openssl-bin nss ca-certificates"

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
do_compile[depends] += "azure-sdk-for-cpp:do_prepare_recipe_sysroot"

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

    #install adu-shell to /usr/lib/adu directory.
    install -d ${D}${libdir}/adu

    install -m 0550 ${S}/src/adu-shell/scripts/adu-swupdate.sh ${D}${libdir}/adu
    chown ${ADUUSER}:${ADUGROUP} ${D}${libdir}/adu

    #set owner for adu-shell
    chmod 0550 ${D}${libdir}/adu/adu-shell
    chown root:${ADUGROUP} ${D}${libdir}/adu/adu-shell

    #set S UID for adu-shell
    chmod u+s ${D}${libdir}/adu/adu-shell
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

        register_content_handler("microsoft/swupdate:1", "{}/libmicrosoft_swupdate_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/swupdate:2", "{}/libmicrosoft_swupdate_2.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/update-manifest", "{}/libmicrosoft_steps_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/update-manifest:4", "{}/libmicrosoft_steps_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/update-manifest:5", "{}/libmicrosoft_steps_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/steps:1", "{}/libmicrosoft_steps_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_handler("microsoft/script:1", "{}/libmicrosoft_script_1.so".format(extensionInstallDir), updateContentRegistrationDirectory, workDir)
        register_content_downloader("{}/libdeliveryoptimization_content_downloader.so".format(extensionInstallDir), contentDownloaderRegistrationDirectory, workDir)
        register_download_handler("microsoft/delta:1", "{}/libmicrosoft_delta_download_handler.so".format(extensionInstallDir), downloadHandlerRegistrationDirectory, workDir)

    except Exception as ex:
        errorMessage = "Failed to create DU Agent extension registration. An exception of type {0} occurred with message:\n{1} and Arguments:\n{2!r}".format(type(ex).__name__, str(ex), ex.args)
        bb.error(errorMessage)
}
do_registerAgentExtensions[depends] += "virtual/fakeroot-native:do_populate_sysroot"
addtask do_registerAgentExtensions after do_install before do_package

fakeroot do_registerAgentExtensions_permissions(){
    chown -R ${ADUUSER}:${ADUGROUP} ${D}${ADUC_UPDATE_CONTENT_HANDLER_EXTENSION_DIR}
}
do_registerAgentExtensions[depends] += "virtual/fakeroot-native:do_populate_sysroot"
addtask do_registerAgentExtensions_permissions after do_registerAgentExtensions before do_package

FILES:${PN} += "${bindir}/AducIotAgent"
FILES:${PN} += "${libdir}/adu/* ${ADUC_DATA_DIR}/* ${ADUC_LOG_DIR}/* ${ADUC_CONF_DIR}/*"
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

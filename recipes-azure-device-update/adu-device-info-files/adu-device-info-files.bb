# Generates a text file with the ADU applicability info
# for manufacturer and model and copies/installs that file into the image.

# Environment variables that can be used to configure the behaviour of this recipe.
# ADU_SOFTWARE_VERSION  The software version for the image/firmware. Will be written to
#                       the version file that is read by ADU Client.

LICENSE="CLOSED"

# Generate the version file
do_compile() {
    echo "${ADU_SOFTWARE_VERSION}" > adu-version
}

# Install the files on the image in /etc
do_install() {
    install -d ${D}${sysconfdir}
    install -m ugo=r adu-version ${D}${sysconfdir}/adu-version
}

FILES:${PN} += "${sysconfdir}/adu-version"

inherit allarch

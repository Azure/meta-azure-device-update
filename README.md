# Introduction 

This project meta-azure-device-update serves as an example of embedding the Device Update agent in a custom Linux-based system.

**Current Version:** 1.3.0 (LAYERVERSION=1)  
**Yocto Compatibility:** Scarthgap (5.0)

> **📋 IMPORTANT:** See [RELEASE-NOTES.md](RELEASE-NOTES.md) for breaking changes, migration guides, and detailed changelog.

> **DISCLAIMER:**  
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

This layer provides recipes and configuration for integrating Azure Device Update (ADU) agent into Yocto-based embedded Linux systems. It includes the ADU client agent, Azure IoT SDK dependencies, Delivery Optimization agent, and supporting infrastructure for over-the-air (OTA) update management.

# Recipes

This layer contains the following recipes:

| Recipe Name      | Description |
| ------------- | ---------- |
| azure-device-update | Device Update for IoT Hub agent that manages OTA updates for IoT devices. Includes SWUpdate content handler, script handler, and step handler for orchestrating multi-step updates.<br/>For more information, see [Device Update for IoT Hub on Github](https://github.com/Azure/iot-hub-device-update) |
| adu-agent-service | Systemd service configuration for the Azure Device Update agent. Manages the AducIotAgent daemon lifecycle and integration with system services. |
| adu-config-setup | **NEW in v1.3:** Creates `/adu/` directory structure, manages symlinks for persistent configuration and data across A/B partition updates, and configures systemd-tmpfiles for log rotation. Consolidates functionality from deprecated adu-log-dir. |
| adu-device-info-files | Installs device information files (manufacturer, model, version) that the ADU agent reads to report device identity to Azure IoT Hub. |
| adu-pub-key | Installs RSA public key for verifying signed update packages. Required for secure update validation. |
| deliveryoptimization-agent | Microsoft Delivery Optimization (DO) client agent for efficient content downloads with peer-to-peer support and bandwidth management. |
| deliveryoptimization-agent-service | Systemd service configuration for the Delivery Optimization agent daemon. |
| deliveryoptimization-sdk | Delivery Optimization SDK libraries required by the ADU agent for download management. |
| azure-iot-sdk-c | The Azure IoT device SDK for C provides libraries for communicating with Azure IoT Hub. Required by Azure Device Update agent.<br/>For more information, see [Azure IoT SDK for C](https://learn.microsoft.com/en-us/azure/iot-hub/iot-hub-device-sdk-c-intro) |
| azure-sdk-for-cpp | Azure SDK for C++ libraries. Required by Azure Blob Storage File Upload Utility.<br/>For more information, see [Azure SDK for C++](https://github.com/Azure/azure-sdk-for-cpp) |
| catch2 | Catch2 testing framework. Required when building ADU agent with unit tests enabled (WITH_ADUC_TESTS=1). |
| msft-gsl | Microsoft's implementation of the C++ Guidelines Support Library (GSL). Provides functions and types suggested by the C++ Core Guidelines. Required by many Microsoft C++ projects including ADU agent.<br/>For more information, see [MS-GSL on GitHub](https://github.com/microsoft/GSL) |

---

# Local Source Development

This layer supports building from local source directories instead of fetching from GitHub. This is useful for:
- Debugging issues in upstream repositories
- Developing new features
- Testing patches before submitting upstream
- Working with private forks

## Supported Repositories

| Repository | Enable Variable | Path Variable | Default Path |
|------------|-----------------|---------------|--------------|
| **Azure Device Update** | `USE_LOCAL_ADU_SOURCE` | `ADU_LOCAL_SOURCE_DIR` | `${TOPDIR}/../../../sources/iot-hub-device-update` |
| **Delivery Optimization** | `USE_LOCAL_DO_SOURCE` | `DO_LOCAL_SOURCE_DIR` | `${TOPDIR}/../../../sources/do-client` |
| **Azure IoT SDK C** | `USE_LOCAL_AZIOT_SDK_C_SOURCE` | `AZIOT_SDK_C_LOCAL_SOURCE_DIR` | `${TOPDIR}/../../../sources/azure-iot-sdk-c` |

## Usage

### Method 1: Set in local.conf (Recommended)

Add to your `build/conf/local.conf`:

```bash
# Enable local source for ADU
USE_LOCAL_ADU_SOURCE = "1"
ADU_LOCAL_SOURCE_DIR = "/path/to/iot-hub-device-update"

# Enable local source for DO (optional)
USE_LOCAL_DO_SOURCE = "1"
DO_LOCAL_SOURCE_DIR = "/path/to/do-client"

# Enable local source for Azure IoT SDK C (optional)
USE_LOCAL_AZIOT_SDK_C_SOURCE = "1"
AZIOT_SDK_C_LOCAL_SOURCE_DIR = "/path/to/azure-iot-sdk-c"
```

### Method 2: Environment Variables

If setting via environment variables on the build host, add them to `BB_ENV_PASSTHROUGH_ADDITIONS` in your `build/conf/local.conf`:

```bash
BB_ENV_PASSTHROUGH_ADDITIONS += "USE_LOCAL_ADU_SOURCE ADU_LOCAL_SOURCE_DIR"
BB_ENV_PASSTHROUGH_ADDITIONS += "USE_LOCAL_DO_SOURCE DO_LOCAL_SOURCE_DIR"
BB_ENV_PASSTHROUGH_ADDITIONS += "USE_LOCAL_AZIOT_SDK_C_SOURCE AZIOT_SDK_C_LOCAL_SOURCE_DIR"
```

Then export the variables before building:

```bash
export USE_LOCAL_ADU_SOURCE=1
export ADU_LOCAL_SOURCE_DIR=~/sources/iot-hub-device-update
bitbake azure-device-update
```

### Method 3: Override in layer.conf

The `conf/layer.conf` provides default values using the weak assignment operator `??=`, which means they can be overridden in your `local.conf` or higher-priority layers without conflict.

## Important Notes

- **GitHub fetch is disabled** when local source is enabled - the build uses your local directory directly
- **Patches are NOT applied** - apply any needed patches manually to your local source
- **Build artifacts** go to `<source>/build-yocto/` subdirectory
- **Default paths are disabled** - local source feature is opt-in, disabled by default
- Changes to local source are immediately reflected in builds (no git fetch)

## Example Workflow

```bash
# Clone the repository
mkdir -p ~/sources
cd ~/sources
git clone https://github.com/Azure/iot-hub-device-update
cd iot-hub-device-update
git checkout my-feature-branch

# Make your changes
vim src/agent/some_file.cpp

# Add to local.conf
echo 'USE_LOCAL_ADU_SOURCE = "1"' >> ~/build/conf/local.conf
echo 'ADU_LOCAL_SOURCE_DIR = "~/sources/iot-hub-device-update"' >> ~/build/conf/local.conf

# Build
cd ~/build
bitbake azure-device-update -c clean
bitbake azure-device-update
```

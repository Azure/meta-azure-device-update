# Introduction 

This project meta-azure-device-update serves as an example of embedding the Device Update agent in a custom Linux-based system.

> **DISCLAIMER:**  
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

This is an example meta layer used for building the Device Update Image Diff Generator library. This library is required by the [Microsoft Delta Download Handler](https://github.com/Azure/iot-hub-device-update/tree/main/src/extensions/download_handlers/plugin_examples/microsoft_delta_download_handler).

# Recipes

This layer contains the following recipes:

| Recipe Name      | Description |
| ------------- | ---------- |
| azure-blob-storage-file-upload-utility | This is a wrapper utility for the Azure SDK for Cpp's Storage SDK for use in DeviceUpdate in IotHub. It provides the functions necessary to upload files passed to the utility to an Azure Blob Storage account using a SAS url by exposing a C interface. <br/> For more information, see [Azure Blog Storage File Upload Utility on Github](https://github.com/Azure/azure-blob-storage-file-upload-utility/blob/main/README.md) |
| azure-device-update | Device Update for IoT Hub is a service that enables you to deploy over-the-air updates (OTA) for your IoT devices.<br/>For more information, see [Device Update for Iot Hub on Github](https://github.com/Azure/iot-hub-device-update) |
| azure-iot-sdk-c | The Azure IoT device SDK is a set of libraries designed to simplify the process of sending messages to and receiving messages from the Azure IoT Hub service. The [Azure IoT device SDK for C](https://learn.microsoft.com/en-us/azure/iot-hub/iot-hub-device-sdk-c-intro) is required by [Device Update for Iot Hub](https://github.com/Azure/iot-hub-device-update)  |
| azure-sdk-for-cpp | [The Azure SDK for C++](https://github.com/Azure/azure-sdk-for-cpp) is required by [Azure Blog Storage File Upload Utility](https://github.com/Azure/azure-blob-storage-file-upload-utility/blob/main/README.md) |
| msft-gsl | The Guidelines Support Library (GSL) contains functions and types that are suggested for use by the C++ Core Guidelines maintained by the Standard C++ Foundation. This repo contains Microsoft's implementation of GSL. <br/> Many projects developed by Microsoft leverage this library.|

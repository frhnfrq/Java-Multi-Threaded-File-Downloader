# Introduction 

File downloader written in Java. Uses thread to download files in multiple connections to fully utilize internet speed.

## Features

* **Multi-threaded. Download files in multiple connections if server accepts range header.**
* **Retries downloading file on failure.**

## How-To

Create an instance of `DownloadManager` using the constructor `DownloadManager(int concurrentDownload)`. Set `DownloadListener` to the instance of `DownloadManager` using `setDownloadListener`. 
Set path and max connection per downloads, `DownloadManager.Config.path = "D:\\downloads"` and ` DownloadManager.Config.connections = 8`
Create an instance of `URL` using the file url and call `downloadManager.download(url)`.

## Example

```java
public static void main(String[] args) {
        try {
            DownloadManager downloadManager = new DownloadManager(100); // 100 concurrent downloads
            DownloadManager.Config.path = "H:\\transfer";
            DownloadManager.Config.connections = 8;
            downloadManager.setDownloadListener(new DownloadListener() {
                @Override
                public void onDownloadStarted(Downloader.DownloadStatus downloadStatus) {
                    System.out.println(downloadStatus.getFileName() + " download started.");
                }

                @Override
                public void onDownloadFinished(Downloader.DownloadStatus downloadStatus) {
                    System.out.println(downloadStatus.getFileName() + " download finished. File size " + downloadStatus.getFileSize() + ", downloaded " + downloadStatus.getDownloadedSize());
                }

                @Override
                public void onDownloadFailed(Downloader.DownloadStatus downloadStatus) {
                    System.out.println(downloadStatus.getFileName() + " download failed. File size " + downloadStatus.getFileSize() + ", downloaded " + downloadStatus.getDownloadedSize());
                }
            });

            downloadManager.download(new URL("https://pop-iso.sfo2.cdn.digitaloceanspaces.com/19.04/amd64/intel/3/pop-os_19.04_amd64_intel_3.iso".replaceAll(" ", "%20")));
        } catch (IOException e) {
            e.printStackTrace();
        }
}
```
## Methods
* `download(URL url)` - Downloads the file from the URL and returns a String containing the process Id.
* `cancelDownload(String processId)` - Cancels a download according to processId.
* `getDownloadStatus(String processId)` - Returns an instance of `DownloadStatus` containing download information according to the process Id.
* `getDownloadList()` - Returns an ArrayList<Downloader> containing all the download processes.

## TODO

* Add pause/resume feature

## Contributions

Please feel free to contribute!!

License
=======

     Copyright 2019 Farhan Farooqui
     
     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
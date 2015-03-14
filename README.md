zip
===================

A Cordova plugin to unzip files in Android and iOS.

##Installation

    cordova plugin add https://github.com/MobileChromeApps/zip.git

##Usage

    zip.unzip(<source zip>, <destination dir>, <callback>, [<progressCallback>]);

Both source and destination arguments can be URLs obtained from the HTML File
interface or absolute paths to files on the device.

The callback argument will be executed when the unzip is complete, or when an
error occurs. It will be called with a single argument, which will be 0 on
success, or -1 on failure.

    zio.compress([files], <fileName>, <callback>, [<progressCallback>]);

Files is array of items (full path) to compress, you can pass folders as well, fileName is again full path with archive name like : /{FULLPATH}/MyFolder/Archive.zip

The progressCallback argument is optional and will be executed whenever a new ZipEntry
has been extracted. E.g.: (does not work on compress yet)

    var progressCallback = function(progressEvent) {
        $( "#progressbar" ).progressbar("value", Math.round((progressEvent.loaded / progressEvent.total) * 100));
    };

The values `loaded` and `total` are the number of compressed bytes processed and total. Total is the
file size of the zip file.

## Changes

* Added iOS and Android compress/zip method
* Zip can include files and folders
* Zip keeps folder tree in archive just as provided

## Notes

* This is cloned/forked version of original zip plugin from  : https://github.com/MobileChromeApps/zip
* The version used in this code may not be latest from their version
* Follow their license
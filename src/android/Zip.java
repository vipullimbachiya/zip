package org.apache.cordova;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.util.zip.ZipOutputStream;

import android.net.Uri;
import android.os.Environment;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi.OpenForReadResult;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import android.util.Log;

public class Zip extends CordovaPlugin {

    private static final String LOG_TAG = "Zip";
    private static final int BUFFER = 2048;
    File destiDir;
	File sourceDir;
	private String[] _files; 
	private String _zipFile; 
	BufferedInputStream origin = null; 
	byte data[] = new byte[BUFFER];
	

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if ("unzip".equals(action)) {
            unzip(args, callbackContext);
            return true;
        }
        else if ("compress".equals(action)) {
            zip(args, callbackContext);
            return true;
        }
        return false;
    }

    private void unzip(final CordovaArgs args, final CallbackContext callbackContext) {
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                unzipSync(args, callbackContext);
            }
        });
    }

    private void zip(final CordovaArgs args, final CallbackContext callbackContext) {
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                zipSync(args, callbackContext);
            }
        });
    }

    // Can't use DataInputStream because it has the wrong endian-ness.
    private static int readInt(InputStream is) throws IOException {
        int a = is.read();
        int b = is.read();
        int c = is.read();
        int d = is.read();
        return a | b << 8 | c << 16 | d << 24;
    }

    private void unzipSync(CordovaArgs args, CallbackContext callbackContext) {
        InputStream inputStream = null;
        try {
            String zipFileName = args.getString(0);
            String outputDirectory = args.getString(1);

            // Since Cordova 3.3.0 and release of File plugins, files are accessed via cdvfile://
            // Accept a path or a URI for the source zip.
            Uri zipUri = getUriForArg(zipFileName);

            // Same for target directory
            Uri outputUri = getUriForArg(outputDirectory);

            CordovaResourceApi resourceApi = webView.getResourceApi();

            File tempFile = resourceApi.mapUriToFile(zipUri);
            if(tempFile == null || !tempFile.exists()) {
                Log.e(LOG_TAG, "Zip file does not exist");
            }

            File outputDir = resourceApi.mapUriToFile(outputUri);
            outputDirectory = outputDir.getAbsolutePath();
            outputDirectory += outputDirectory.endsWith(File.separator) ? "" : File.separator;
            if(outputDir == null || (!outputDir.exists() && !outputDir.mkdirs())){
                throw new FileNotFoundException("File: \"" + outputDirectory + "\" not found");
            }

            OpenForReadResult zipFile = resourceApi.openForRead(zipUri);
            ProgressEvent progress = new ProgressEvent();
            progress.setTotal(zipFile.length);

            inputStream = new BufferedInputStream(zipFile.inputStream);
            inputStream.mark(10);
            int magic = readInt(inputStream);

            if (magic != 875721283) { // CRX identifier
                inputStream.reset();
            } else {
                // CRX files contain a header. This header consists of:
                //  * 4 bytes of magic number
                //  * 4 bytes of CRX format version,
                //  * 4 bytes of public key length
                //  * 4 bytes of signature length
                //  * the public key
                //  * the signature
                // and then the ordinary zip data follows. We skip over the header before creating the ZipInputStream.
                readInt(inputStream); // version == 2.
                int pubkeyLength = readInt(inputStream);
                int signatureLength = readInt(inputStream);

                inputStream.skip(pubkeyLength + signatureLength);
                progress.setLoaded(16 + pubkeyLength + signatureLength);
            }

            // The inputstream is now pointing at the start of the actual zip file content.
            ZipInputStream zis = new ZipInputStream(inputStream);
            inputStream = zis;

            ZipEntry ze;
            byte[] buffer = new byte[32 * 1024];
            boolean anyEntries = false;

            while ((ze = zis.getNextEntry()) != null)
            {
                anyEntries = true;
                String compressedName = ze.getName();

                if (ze.isDirectory()) {
                   File dir = new File(outputDirectory + compressedName);
                   dir.mkdirs();
                } else {
                    File file = new File(outputDirectory + compressedName);
                    file.getParentFile().mkdirs();
                    if(file.exists() || file.createNewFile()){
                        Log.w("Zip", "extracting: " + file.getPath());
                        FileOutputStream fout = new FileOutputStream(file);
                        int count;
                        while ((count = zis.read(buffer)) != -1)
                        {
                            fout.write(buffer, 0, count);
                        }
                        fout.close();
                    }

                }
                progress.addLoaded(ze.getCompressedSize());
                updateProgress(callbackContext, progress);
                zis.closeEntry();
            }

            // final progress = 100%
            progress.setLoaded(progress.getTotal());
            updateProgress(callbackContext, progress);

            if (anyEntries)
                callbackContext.success();
            else
                callbackContext.error("Bad zip file");
        } catch (Exception e) {
            String errorMessage = "An error occurred while unzipping.";
            callbackContext.error(errorMessage);
            Log.e(LOG_TAG, errorMessage, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }
    
    

    private void zipSync(CordovaArgs args, CallbackContext callbackContext) {
        InputStream inputStream = null;
        try {
            JSONArray _files = args.getJSONArray(0);
            String outputDirectory = args.getString(1);
            String zpfileName = outputDirectory.substring(outputDirectory.lastIndexOf("/")+1);
            String dirName = outputDirectory.substring(0, outputDirectory.lastIndexOf("/"));
            Uri outputUri = getUriForArg(dirName);
            boolean anyEntries = false;
            CordovaResourceApi resourceApi = webView.getResourceApi();
            String removePath = "";
            if(_files.length()>0)
            {            	    
            	    String _zipFile = Environment.getExternalStorageDirectory().toString()+"/Offline/" + zpfileName;            	    
            		FileOutputStream dest = new FileOutputStream(_zipFile); 
   				ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
   				for(int i=0; i < _files.length(); i++) {
   	                anyEntries = true;   	                
   	                String fileName = _files.get(i).toString();
   	                Uri fileUri = getUriForArg(fileName);
   	                File fileItem = resourceApi.mapUriToFile(fileUri);  
   	                if(i==0){
   	                	removePath=fileItem.getAbsoluteFile().getParent();
   	                }  	                	
   	                ZipData(out, fileItem, removePath);
   	            }
   				out.close();
            }
            
            if (anyEntries)
                callbackContext.success();
            else
                callbackContext.error("Problem while creating zip");
        } catch (Exception e) {
            String errorMessage = "An error occurred while zipping.";
            callbackContext.error(errorMessage);
            Log.e(LOG_TAG, errorMessage, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void updateProgress(CallbackContext callbackContext, ProgressEvent progress) throws JSONException {
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, progress.toJSONObject());
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
    }

    private Uri getUriForArg(String arg) {
        CordovaResourceApi resourceApi = webView.getResourceApi();
        Uri tmpTarget = Uri.parse(arg);
        return resourceApi.remapUri(
                tmpTarget.getScheme() != null ? tmpTarget : Uri.fromFile(new File(arg)));
    }

    private void ZipData(ZipOutputStream out, File file, String removePath) {
    	  // TODO Auto-generated method stub
    	  try {
    	   if (file.isDirectory()) {
    	    File[] files = file.listFiles();
    	    for (File file1 : files) {
    	     if (file1.isDirectory()) {
    	      ZipData(out, file1, removePath);
    	     } else {
    	      FileInputStream fi = new FileInputStream(
    	        file1.getAbsoluteFile());
    	      origin = new BufferedInputStream(fi, BUFFER);

    	      String filezip = file1.getAbsolutePath().replace(removePath+"/"
    	          .toString(), "");
    	      Log.i("file >>", filezip);
    	      ZipEntry entry = new ZipEntry(filezip);
    	      out.putNextEntry(entry);

    	      int count;
    	      while ((count = origin.read(data, 0, BUFFER)) != -1) {
    	       out.write(data, 0, count);
    	      }

    	      origin.close();
    	     }
    	    }
    	   } else {
    	    FileInputStream fi = new FileInputStream(file.getAbsoluteFile());
    	    origin = new BufferedInputStream(fi, BUFFER);

    	    String filezip = file.getAbsolutePath().replace(removePath+"/",
    	      "");
    	    Log.i("file >>", filezip);
    	    ZipEntry entry = new ZipEntry(filezip);
    	    out.putNextEntry(entry);
    	    int count;
    	    while ((count = origin.read(data, 0, BUFFER)) != -1) {
    	     out.write(data, 0, count);
    	    }

    	    origin.close();
    	   }
    	  } catch (Exception e) {
    	   // TODO: handle exception
    	   e.printStackTrace();
    	  }


    	 }

    
    private static class ProgressEvent {
        private long loaded;
        private long total;
        public long getLoaded() {
            return loaded;
        }
        public void setLoaded(long loaded) {
            this.loaded = loaded;
        }
        public void addLoaded(long add) {
            this.loaded += add;
        }
        public long getTotal() {
            return total;
        }
        public void setTotal(long total) {
            this.total = total;
        }
        public JSONObject toJSONObject() throws JSONException {
            return new JSONObject(
                    "{loaded:" + loaded +
                    ",total:" + total + "}");
        }
    }
}

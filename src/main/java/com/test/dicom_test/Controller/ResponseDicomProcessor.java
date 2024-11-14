package com.test.dicom_test.Controller;

public class ResponseDicomProcessor {
	private Boolean success;
    private String message;
    private String folderPath;
    
    public ResponseDicomProcessor(Boolean success, String message, String folderPath) {
    	this.success = success;
        this.message = message;
        this.folderPath = folderPath;
    }
    
    public boolean getSucess() {
        return success;
    }

    public void setStatus(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.folderPath = message;
    }
    
    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

}

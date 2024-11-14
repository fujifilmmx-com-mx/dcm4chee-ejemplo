package com.test.dicom_test.Controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api")
public class DicomController {
	@GetMapping("/hello")
    public String sayHello() {
        return "Hello, World!";
    }
	
	@GetMapping("/dicom/{numacc}")
    public ApiResponse<String> getStudyImage(@PathVariable String numacc) throws IOException, InterruptedException {
		System.out.println("--->NumAcc: " + numacc);
		if(numacc != null && numacc != "") {			
			ResponseDicomProcessor path = DicomProcessor.process(numacc);
			System.out.println(path.getFolderPath());
			System.out.println(path.getMessage());
			if(path.getSucess())
				return new ApiResponse<>(HttpStatus.OK.value(), "Success", path.getFolderPath());
			else
				return new ApiResponse<>(HttpStatus.OK.value(), "Error: " + path.getMessage(), path.getFolderPath() );
		} 
		else {
			return new ApiResponse<>(HttpStatus.OK.value(), "Success", "Validar la informacion.");
			}
    }
}

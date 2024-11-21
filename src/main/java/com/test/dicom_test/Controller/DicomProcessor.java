package com.test.dicom_test.Controller;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;

public class DicomProcessor {
	
    // Ruta storescp
    static String exePath = "C:\\\\dcmtk-3.6.8\\\\bin\\\\storescp.exe";
    
    // Ruta alamcenamieto de las imagenes
    static String LocalFolderSCP ="C:\\IMAGES";

	// ** Parametros locales **
	// IP Local
	static String localIP = "172.16.70.203";
	// Puerto donde estaremos escuchando para alacenar
	static Integer localPort = 1105;
	// AE title conocido por la regla Query And Retrivee
	static String localAETitle = "DCM4CHEE3";
	// Prefijo con el cual inicia el nombre de la carpeta que alamcena los estudios
	static String PrefixPath = "DCM";

	// ** Parametros locales **
	// AE TITLE al que se le solicitan las imagens
	static String remoteAETitle = "SYN7DCM";
	// IP del servidor remoto al que se le solicitan las imagenes
	static String remoteIP = "172.16.70.67";
	// Puerto al que se le solicitan las imagenes
	static Integer remotePort = 104;
	
	public static ResponseDicomProcessor process(String numAcc) throws IOException, InterruptedException {
		// Estudio
		String accessionNumber = "";
		System.out.println("numAcc: " + numAcc);
		ResponseDicomProcessor response = new ResponseDicomProcessor(false, "", "");
		String folderStudyPath ="";
		if(numAcc != "")
			accessionNumber = numAcc;
		else
			return  new ResponseDicomProcessor(false, "Sin numero de acceso", "");
		
		ApplicationEntity locAE = new ApplicationEntity();
		locAE.setAETitle(localAETitle);
		locAE.setInstalled(true);
		
		Connection localConn = new Connection("loc_conn", localIP);
		localConn.setCommonName("loc_conn");
		localConn.setHostname(localIP);
		//localConn.setPort(localPort);
		localConn.setProtocol(Connection.Protocol.DICOM);
		localConn.setInstalled(true);
		locAE.addConnection(localConn);
		
		
		correrSCP(localAETitle, localPort,PrefixPath , LocalFolderSCP);
		
		
		// Configurar la entidad de aplicaciÃ³n remota
		ApplicationEntity remAE = new ApplicationEntity();
		remAE.setAETitle(remoteAETitle);
		remAE.setInstalled(true);
		
		Connection remoteConn = new Connection();
		remoteConn.setCommonName("rem_conn");
		remoteConn.setHostname(remoteIP);
		remoteConn.setPort(remotePort);
		remoteConn.setProtocol(Connection.Protocol.DICOM);
		remoteConn.setInstalled(true);
		remAE.addConnection(remoteConn);
		
		AAssociateRQ assocReq = new AAssociateRQ();
		assocReq.setCalledAET(remAE.getAETitle());
		assocReq.setCallingAET(locAE.getAETitle());
		assocReq.setApplicationContext("1.2.840.10008.3.1.1.1");
		assocReq.setImplClassUID("1.2.40.0.13.1.3");
		assocReq.setImplVersionName("dcm4che-5.12.0");
		assocReq.setMaxPDULength(16384);
		assocReq.setMaxOpsInvoked(0);
		assocReq.setMaxOpsPerformed(0);
		
		
		assocReq.addPresentationContext(new PresentationContext(
			1, "1.2.840.10008.1.1", "1.2.840.10008.1.2"));
		
		assocReq.addPresentationContext(new PresentationContext(
			2, "1.2.840.10008.5.1.4.1.2.2.1", "1.2.840.10008.1.2"));
		
		assocReq.addPresentationContext(new PresentationContext(
			3, "1.2.840.10008.1.1", "1.2.840.10008.1.2"));
		
		assocReq.addPresentationContext(new PresentationContext(
			4, "1.2.840.10008.5.1.4.1.2.2.2", "1.2.840.10008.1.2"));
		
		assocReq.addRoleSelection(new RoleSelection(UID.Verification, /* is SCU? */ true, /* is SCP? */ false));
		
		// Configurar el dispositivo
		Device device = new Device("device");
		device.addConnection(localConn);
		device.addApplicationEntity(locAE);
		
		// Configurar el servicio de ejecuciÃ³n
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		device.setExecutor(executorService);
		device.setScheduledExecutor(scheduledExecutorService);
		
		Association assoc = null;
		try {	
		
		
		assoc = locAE.connect(localConn, remoteConn, assocReq);
        String studyInstanceUID = executeCFind(assoc, accessionNumber);
        System.out.println("accessionNumber: " + accessionNumber +  "---> studyInstanceUID " + studyInstanceUID );
        assoc.release();

        if (studyInstanceUID != null && !studyInstanceUID.isEmpty()) {
            String folderTarget = executeCMove(locAE, localConn, remoteConn, assocReq, studyInstanceUID, accessionNumber);
            if (!folderTarget.isEmpty()) {
                response.setMessage("El estudio " + numAcc + " está listo");
                response.setFolderPath(folderTarget);
                response.setStatus(true);
            } else {
                response.setMessage("Hubo un problema al procesar el estudio");
                response.setStatus(false);
            }
        } else {
            response.setMessage("No se encontró el estudio");
            response.setStatus(false);
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	        response.setMessage("Error al procesar la solicitud: " + e.getMessage());
	        response.setStatus(false);
	    } finally {
	        shutdownExecutors(executorService, scheduledExecutorService);
	        if (assoc != null && assoc.isReadyForDataTransfer()) {
	            assoc.release();
	        }
	    }
	
	    return response;

		
	}
	
	private static void ProcessFolder(String folderTarget) {
    	System.out.println("Pat: " + folderTarget);
    	Path directorioInicial = Paths.get(folderTarget); // Reemplaza con el path deseado

        try {
            // Recorre todos los archivos en el directorio y sus subdirectorios
            Files.walkFileTree(directorioInicial, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    System.out.println("Archivo encontrado: " + file.toString());
                    leeArchivoDicom(file.toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.println("Error al acceder al archivo: " + file.toString());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

    public static void correrSCP(String SCPAE, Integer SCPPort, String FolderPrefix, String LocalStoragePath) {


        // Validar parámetros de entrada
        if (SCPAE == null || SCPAE.isEmpty() || FolderPrefix == null || FolderPrefix.isEmpty() || LocalStoragePath == null || LocalStoragePath.isEmpty()) {
            throw new IllegalArgumentException("Los parámetros SCPAE, FolderPrefix, y LocalStoragePath no deben estar vacíos.");
        }


        // Verificar si el puerto está abierto
        if (isPortOpen("localhost", SCPPort, 2000)) { // "localhost" y timeout de 2000 ms
        	System.out.println("El puerto " + SCPPort + " ya está en uso. No se ejecutará el proceso.");
            return;
        }
        
        try {
            // Crear comando como lista de strings
            List<String> command = Arrays.asList(
                    exePath, "-d", "-v", "-aet", SCPAE, "-od", LocalStoragePath, "+xs", "--fork" ,"--sort-on-study-uid", FolderPrefix, SCPPort.toString()
            );

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            // Iniciar el proceso
            Process process = processBuilder.start();
            System.out.println("Programa ejecutado y ejecutándose de forma independiente.");


        } catch (IOException e) {
        	System.out.println("Error al iniciar el proceso: " + e.getMessage());
        } 
    }
	
	@SuppressWarnings("deprecation")
	public static void leeArchivoDicom(String archivoDicom)
	{
		
		File file = new File(archivoDicom);
        try (DicomInputStream dicomInputStream = new DicomInputStream(file)) {
            // Lee los metadatos DICOM
			Attributes attributes = dicomInputStream.readDataset(-1,-1);

			System.out.println("Datos del archivo: " + archivoDicom);
            // Ejemplo de extracción de algunos atributos
            String patientName = attributes.getString(Tag.PatientName);
            String PatientBirthDate = attributes.getString(Tag.PatientBirthDate );
            String PatientSex = attributes.getString(Tag.PatientSex );
            
            String studyDate = attributes.getString(Tag.StudyDate);
            String AccessionNumber = attributes.getString(Tag.AccessionNumber);
            String StudyInstanceUID = attributes.getString(Tag.StudyInstanceUID);
            String UID = attributes.getString(Tag.UID);
            String InstanceNumber = attributes.getString(Tag.InstanceNumber);
            String ImageType = attributes.getString(Tag.ImageType);
            String ImageID = attributes.getString(Tag.ImageID);
            String RetrieveAETitle = attributes.getString(Tag.RetrieveAETitle);
            String SOPInstanceUID = attributes.getString(Tag.SOPInstanceUID);
            String ReferringPhysicianName = attributes.getString(Tag.ReferringPhysicianName );
            String StudyDescription = attributes.getString(Tag.StudyDescription );
            String SeriesDescription = attributes.getString(Tag.SeriesDescription  );
            String TransferSyntaxUID = attributes.getString(Tag.TransferSyntaxUID  );
            String StudyID = attributes.getString(Tag.StudyID  );
            
            System.out.println("Nombre del Paciente: " + patientName);
            System.out.println("Fecha de Estudio: " + studyDate);
            System.out.println("StudyInstanceUID: " + StudyInstanceUID);
            System.out.println("AccessionNumber: " + AccessionNumber);
            System.out.println("UID: " + UID);
            System.out.println("InstanceNumber: " + InstanceNumber);
            System.out.println("ImageType: " + ImageType);
            System.out.println("ImageID: " + ImageID);
            System.out.println("RetrieveAETitle: " + RetrieveAETitle);
            System.out.println("SOPInstanceUID: " + SOPInstanceUID);
            System.out.println("PatientBirthDate: " + PatientBirthDate);
            System.out.println("PatientSex: " + PatientSex);
            System.out.println("ReferringPhysicianName: " + ReferringPhysicianName);
            System.out.println("StudyDescription: " + StudyDescription);  
            System.out.println("SeriesDescription: " + SeriesDescription); 
            System.out.println("TransferSyntaxUID: " + TransferSyntaxUID); 
            System.out.println("StudyID: " + StudyID); 
            
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	// Método para verificar si un puerto está abierto
	private static boolean isPortOpen(String host, int port, int timeout) {
	    try (Socket socket = new Socket()) {
	        socket.connect(new InetSocketAddress(host, port), timeout);
	        return true; // Conexión exitosa significa que el puerto está en uso
	    } catch (IOException e) {
	        return false; // Error significa que el puerto está cerrado
	    }
	}
	
	private static ApplicationEntity configureLocalAE() {
		System.out.println("Inciio configureLocalAE.");
	    ApplicationEntity locAE = new ApplicationEntity();
	    locAE.setAETitle("LOCAL_AE");
	    locAE.setInstalled(true);

	    Connection localConn = new Connection("loc_conn", "127.0.0.1");
	    localConn.setProtocol(Connection.Protocol.DICOM);
	    locAE.addConnection(localConn);
	    System.out.println("Fin configureLocalAE.");
	    return locAE;
	}
	
	private static ApplicationEntity configureRemoteAE() {
		System.out.println("Inicio configureRemoteAE.");
	    ApplicationEntity remAE = new ApplicationEntity();
	    remAE.setAETitle("REMOTE_AE");
	    remAE.setInstalled(true);

	    Connection remoteConn = new Connection("rem_conn", "remoteHost", 104);
	    remoteConn.setProtocol(Connection.Protocol.DICOM);
	    remAE.addConnection(remoteConn);
	    System.out.println("Fin configureRemoteAE.");
	    return remAE;
	}
	
	private static AAssociateRQ createAssociationRequest(ApplicationEntity locAE, ApplicationEntity remAE) {
		System.out.println("Inicio createAssociationRequest");
	    AAssociateRQ assocReq = new AAssociateRQ();
	    assocReq.setCalledAET(remAE.getAETitle());
	    assocReq.setCallingAET(locAE.getAETitle());
	    assocReq.addPresentationContext(new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelFind, UID.ImplicitVRLittleEndian));
	    System.out.println("Fin createAssociationRequest");
	    return assocReq;
	}
	
	private static String executeCFind(Association assoc, String accessionNumber) throws IOException, InterruptedException {
		System.out.println("inicio executeCFind " + accessionNumber);
	    Attributes keys = new Attributes();
	    keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
		keys.setString(Tag.AccessionNumber, VR.SH, accessionNumber); // UID del estudio
		keys.setString(Tag.StudyInstanceUID, VR.UI); // StudyInstanceUID
		keys.setString(Tag.StudyDate, VR.DA); // StudyInstanceUID
		keys.setString(Tag.PatientName, VR.PN); // StudyInstanceUID

	    DimseRSP rspFind = assoc.cfind(UID.StudyRootQueryRetrieveInformationModelFind, Priority.NORMAL, keys, null, 0);
	    String studyInstanceUID = null;

	    while (rspFind.next()) {
	        Attributes attrs = rspFind.getDataset();
	        if (attrs != null) {
	            studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
	        }
	    }
	    System.out.println("Fin executeCFind " + accessionNumber);
	    return studyInstanceUID;
	}

	private static String executeCMove(ApplicationEntity locAE, Connection localConn, Connection remoteConn, AAssociateRQ assocReq, String studyInstanceUID, String accnum) throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException {
		System.out.println("Inicio executeCMove " + accnum);
	    Association assoc2 = locAE.connect(localConn, remoteConn, assocReq);
	    
	    Attributes keysMove = new Attributes();
		keysMove.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY"); // Nivel de consulta
		keysMove.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID); // UID del estudio
		System.out.println("**studyInstanceUID: " +studyInstanceUID);
	    Boolean resultMoveSCU = false;
		// Ejecutar el C-MOVE        
		DimseRSP result =  assoc2.cmove(UID.StudyRootQueryRetrieveInformationModelMove, Priority.NORMAL, keysMove, null, locAE.getAETitle());
		while(result.next()) {
			Attributes cmd = result.getCommand();
			int status = cmd.getInt(Tag.Status, -1);
			if (status == 0) {
				System.out.println("C-MOVE completado exitosamente.");
				resultMoveSCU=true;
			} else if (status == 0xFF00) {
				System.out.println("C-MOVE en progreso...");
			} else  if (status == 0xB000) {
		        System.out.println("C-MOVE completado con fallos parciales: " + Integer.toHexString(status));
		        // Extraer e imprimir detalles de fallas parciales.
		        Attributes failedSuboperations = cmd.getNestedDataset(Tag.FailedSOPInstanceUIDList);
		        if (failedSuboperations != null) {
		            System.out.println("UIDs fallidos: " + failedSuboperations);
		        }
		    } else {
		        System.out.println("C-MOVE falló con el estado: " + Integer.toHexString(status));
		    }
		}
		String folderTarget = "";
		if (resultMoveSCU)
		{
			System.out.println("--->studyInstanceUID: " + studyInstanceUID);
			folderTarget = LocalFolderSCP + "\\" + PrefixPath + "_" + studyInstanceUID + "\\";
			System.out.println("folderTarget: " + folderTarget);
			ProcessFolder(folderTarget);
			System.out.println("Fin del c-move accnum");
			return folderTarget;
		}
		else
		{
			System.out.println("Sin resultados en la carpeta");
			System.out.println("Fin del c-move accnum");
			return "Sin resultados en la carpeta";
		}
	}
	
	private static void shutdownExecutors(ExecutorService executor, ScheduledExecutorService scheduledExecutor) {
	    executor.shutdown();
	    scheduledExecutor.shutdown();
	}
}

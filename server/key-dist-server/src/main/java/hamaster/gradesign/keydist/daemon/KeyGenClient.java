package hamaster.gradesign.keydist.daemon;

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import hamaster.gradesgin.ibe.IBEPrivateKey;
import hamaster.gradesgin.ibe.IBEPublicParameter;
import hamaster.gradesgin.ibs.IBSCertificate;
import hamaster.gradesgin.util.Hex;
import hamaster.gradesgin.util.IBECapsule;
import hamaster.gradesgin.util.IBECapsuleAESImpl;
import hamaster.gradesign.keydist.client.Encoder;
import hamaster.gradesign.keygen.IBECSR;
import hamaster.gradesign.keygen.IdentityDescription;
import hamaster.gradesign.keygen.SimpleRESTResponse;
import hamaster.gradesign.keygen.entity.IdentityDescriptionEntity;

@Service
public class KeyGenClient {

    @Value("${hamaster.gradesign.keydist.genserver}")
    private String keyGenServereURL;

    @Value("${hamaster.gradesign.keydist.system}")
    private String systemIDStr;

    @Value("${hamaster.gradesign.keydist.server_id}")
    private String serverID;

    @Value("${hamaster.gradesign.keydist.server_key_valid:1000000000000}")
    private Long serverKeyValidPeriod;

    @Value("${hamaster.gradesign.keydist.server_key_dir}")
    private String serverKeyLocation;

    private final static String SERVER_KEY_FILE = "ibedist.key";
    private final static String SERVER_KEY_FILE_CONTENT = "content";
    private final static String SERVER_KEY_FILE_CRYPT_KEY = "key";

    private Integer currentSystemID;
    private IBSCertificate serverCertificate;
    private IBEPrivateKey serverPrivateKey;
    private Encoder base64;
    private RestTemplate restTemplate;

    private Map<Integer, String> systemIDs;
    private Map<Integer, IBEPublicParameter> systemParameters;

    @Autowired
    public KeyGenClient(RestTemplateBuilder restTemplateBuilder, @Qualifier("base64Encoder") Encoder base64) {
        this.restTemplate = requireNonNull(restTemplateBuilder).build();
        this.base64 = requireNonNull(base64);
        currentSystemID = -1;
        systemIDs = new ConcurrentHashMap<Integer, String>();
        systemParameters = new ConcurrentHashMap<Integer, IBEPublicParameter>();
    }

    public void init() {
        SimpleRESTResponse resp = restTemplate.getForObject(String.format("%s/system/%s/number", keyGenServereURL, systemIDStr), SimpleRESTResponse.class);
        if (resp.getResultCode() == 0) {
            currentSystemID = (Integer) resp.getPayload();
            System.out.println("Connected to keygen server, default system: " + currentSystemID);
        } else {
            System.err.println(resp);
        }
        @SuppressWarnings("unchecked")
        Map<Integer, String> allSystem = restTemplate.getForObject(String.format("%s/system/all", keyGenServereURL), Map.class);
        systemIDs.putAll(allSystem);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> allSystemParameters = restTemplate.getForObject(String.format("%s/system/allparam", keyGenServereURL), Map.class);
        for (String systemID : allSystemParameters.keySet()) {
            Map<String, String> base64EncodedParameter = allSystemParameters.get(systemID);
            IBEPublicParameter decodedParameter = new IBEPublicParameter();
            decodedParameter.setPairing(base64.decode(base64EncodedParameter.get("pairing")));
            decodedParameter.setParamG(base64.decode(base64EncodedParameter.get("paramG")));
            decodedParameter.setParamG1(base64.decode(base64EncodedParameter.get("paramG1")));
            decodedParameter.setParamH(base64.decode(base64EncodedParameter.get("paramH")));
            systemParameters.put(Integer.parseInt(systemID), decodedParameter);
        }
    }

    public IBEPrivateKey serverPrivateKey() {
        if (serverPrivateKey != null)
            return serverPrivateKey;
        prepareServerKeys();
        return serverPrivateKey;
    }

    public IBSCertificate serverCertificate() {
        if (serverCertificate != null)
            return serverCertificate;
        prepareServerKeys();
        return serverCertificate;
    }

    private synchronized void prepareServerKeys() {
        File folder = new File(serverKeyLocation);
        if (folder.isDirectory()) {
            // TODO check file permission
            Properties props = new Properties();
            InputStream key = null;
            try {
                key = new FileInputStream(new File(folder, SERVER_KEY_FILE));
                props.load(key);
            } catch (IOException e) {
                return;
            } finally {
                try {
                    if (key != null)
                        key.close();
                } catch (IOException e) {
                }
            }
            // both server key and its decryption key are stored in hex format
            String serverIdContent = props.getProperty(SERVER_KEY_FILE_CONTENT);
            String keyContent = props.getProperty(SERVER_KEY_FILE_CRYPT_KEY);
            byte[] raw = Hex.unhex(serverIdContent);
            ByteArrayInputStream in = new ByteArrayInputStream(raw);
            IBECapsule capsule = new IBECapsuleAESImpl();
            capsule.setKey(Hex.unhex(keyContent));
            IdentityDescription id = null;
            try {
                capsule.readExternal(in);
                id = (IdentityDescription) capsule.getDataAsObject();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                capsule.close();
            }
            serverPrivateKey = id == null ? null : id.getPrivateKey();
            serverCertificate = id == null ? null : id.getCertificate();
        }
    }

    public void setupServerKeys() {
        File folder = new File(serverKeyLocation);
        if (!folder.exists())
            folder.mkdirs();
        IBECSR request = new IBECSR();
        request.setApplicationDate(new Date());
        request.setIdentityString(serverID);
        request.setIbeSystemId(currentSystemID);
        request.setPeriod(serverKeyValidPeriod);
        ResponseEntity<SimpleRESTResponse> resp = restTemplate.postForEntity(String.format("%s/singleid", keyGenServereURL), request, SimpleRESTResponse.class);
        if (resp.hasBody()) {
            Properties serverKey = new Properties();
            IdentityDescriptionEntity id = (IdentityDescriptionEntity) resp.getBody().getPayload();
            serverKey.setProperty(SERVER_KEY_FILE_CONTENT, Hex.hex(id.getEncryptedIdentityDescription()));
            serverKey.setProperty(SERVER_KEY_FILE_CRYPT_KEY, "???");  // TODO generate random session key and encrypt it with IBE
            File key = new File(folder, SERVER_KEY_FILE);
            if (key.exists()) {
                File backup = new File(folder, String.format("%s_%d", SERVER_KEY_FILE, System.currentTimeMillis()));
                if (key.renameTo(backup)) {
                    // TODO log rename incident
                } else {
                    // TODO log error
                    return;
                }
            }
            OutputStream out = null;
            try {
                key.createNewFile();
                out = new FileOutputStream(key);
                serverKey.store(out, "server key");
                out.flush();
            } catch (IOException e) {
                // TODO log error
                e.printStackTrace();
            } finally {
                try {
                    if (out != null)
                        out.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public String getSystemIDStr(Integer systemID) {
        return systemIDs.get(systemID);
    }

    public IBEPublicParameter getKeyGenServerPublicParameter(Integer systemID) {
        return systemParameters.get(systemID);
    }

    public Integer getCurrentSystemID() {
        return currentSystemID;
    }

    public String getKeyGenServereURL() {
        return keyGenServereURL;
    }
}

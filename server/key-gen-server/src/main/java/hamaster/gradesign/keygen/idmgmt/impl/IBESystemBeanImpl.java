package hamaster.gradesign.keygen.idmgmt.impl;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import hamaster.gradesgin.ibe.IBEPrivateKey;
import hamaster.gradesgin.ibe.IBEPublicParameter;
import hamaster.gradesgin.ibe.IBESystemParameter;
import hamaster.gradesgin.ibe.core.IBEEngine;
import hamaster.gradesgin.ibs.IBSCertificate;
import hamaster.gradesgin.util.Hash;
import hamaster.gradesgin.util.Hex;
import hamaster.gradesign.keygen.IBESystem;
import hamaster.gradesign.keygen.entity.IBESystemEntity;
import hamaster.gradesign.keygen.idmgmt.IBESystemBean;
import hamaster.gradesign.keygen.key.SecureKeyIO;
import hamaster.gradesign.keygen.repo.IBESystemRepository;

@Service
public class IBESystemBeanImpl implements IBESystemBean {

    private IBESystemRepository repo;
    private SecureKeyIO secureKeyIO;

    private Map<Integer, IBEPrivateKey> serverPrivateKeys;

    @Autowired
    public IBESystemBeanImpl(IBESystemRepository repo, SecureKeyIO secureKeyIO) {
        this.repo = requireNonNull(repo);
        this.secureKeyIO = requireNonNull(secureKeyIO);
        this.serverPrivateKeys = new ConcurrentHashMap<Integer, IBEPrivateKey>();
    }

    @Override
    public IBEPrivateKey getPrivateKeyForSystem(Integer systemID) {
        String publicKey = null;
        IBESystem system = null;
        IBEPrivateKey key = serverPrivateKeys.get(systemID);
        if (key != null)
            return key;
        try {
            IBESystemEntity entity = repo.getOne(systemID);
            publicKey = entity.getSystemOwner();
            system = entity.getSystem(secureKeyIO.getSystemAccessPassword(systemID));
        } catch (EntityNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        key = IBEEngine.keygen(system.getParameter(), publicKey);
        serverPrivateKeys.put(systemID, key);
        return key;
    }

    @Override
    public IBESystem createIBSSystem(String owner, byte[] pairing, byte[] password) {
        IBESystemParameter systemParameter = IBEEngine.setup(pairing);
        IBSCertificate fake = new IBSCertificate();
        fake.setPublicParameter(systemParameter.getPublicParameter());
        IBSCertificate certificate = IBEEngine.generateCertificate(owner, fake, new Date(), Integer.MAX_VALUE);
        IBESystem system = new IBESystem();
        system.setCryptionKey(password);
        system.setCertificate(certificate);
        system.setParameter(systemParameter);
        IBESystemEntity entity = new IBESystemEntity();
        entity.setSystem(system);
        entity.setSystemOwner(owner);
        entity.setPublicParameter(system.getParameter().getPublicParameter().toByteArray());
        entity.setSystemKeyHash(Hex.hex(Hash.sha512(password)));
        repo.save(entity);
        return system;
    }

    public void generateDefaultSystem() {
        String owner = "IBE_SERVER";
        int systemID = 0;
        String pairing = "type a q 8780710799663312522437781984754049815806883199414208211028653399266475630880222957078625179422662221423155858769582317459277713367317481324925129998224791 h 12016012264891146079388821366740534204802954401251311822919615131047207289359704531102844802183906537786776 r 730750818665451621361119245571504901405976559617 exp2 159 exp1 107 sign1 1 sign0 1 ";
        IBESystem demo = createIBSSystem(owner, pairing.getBytes(), secureKeyIO.getSystemAccessPassword(systemID));
        serverPrivateKeys.put(systemID, IBEEngine.keygen(demo.getParameter(), owner));
    }

    public Map<Integer, String> list(int page, int amount) {
        Page<IBESystemEntity> systems = repo.findAll(PageRequest.of(page, amount));
        Map<Integer, String> results = new HashMap<Integer, String>((int) (systems.getSize() * 1.4));
        for (IBESystemEntity system : systems)
            results.put(system.getSystemId(), system.getSystemOwner());
        return results;
    }

    @Override
    public long totalSystems() {
        return repo.count();
    }

    @Override
    public Integer getIDByName(String owner) {
        Optional<IBESystemEntity> system = repo.findByOwner(owner);
        if (system.isPresent())
            return system.get().getSystemId();
        return -1;
    }

    @Override
    public Map<Integer, String> listAll() {
        List<IBESystemEntity> systems = repo.findAll();
        Map<Integer, String> results = new HashMap<Integer, String>();
        for (IBESystemEntity system : systems)
            results.put(system.getSystemId(), system.getSystemOwner());
        return results;
    }

    @Override
    public Map<Integer, IBEPublicParameter> listAllParameters() {
        List<IBESystemEntity> systems = repo.findAll();
        Map<Integer, IBEPublicParameter> results = new HashMap<Integer, IBEPublicParameter>();
        for (IBESystemEntity system : systems) {
            try {
                results.put(system.getSystemId(), IBEPublicParameter.fromByteArray(system.getPublicParameter()));
            } catch (ClassNotFoundException | IOException e) {
            }
        }
        return results;
    }
}

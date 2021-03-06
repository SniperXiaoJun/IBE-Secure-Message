package hamaster.gradesign.keydist.service.impl;

import static java.util.Objects.requireNonNull;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.BatchSqlUpdate;
import org.springframework.stereotype.Service;

import hamaster.gradesgin.util.Hash;
import hamaster.gradesgin.util.Hex;
import hamaster.gradesign.keydist.dao.IDRequestDAO;
import hamaster.gradesign.keydist.entity.IDRequest;
import hamaster.gradesign.keydist.entity.User;
import hamaster.gradesign.keydist.service.IDRequestService;
import hamaster.gradesign.keygen.IBECSR;

@Service
public class IDRequestServiceImpl implements IDRequestService {

    private IDRequestDAO idRequestRepo;
    private DataSource dataSource;

    @Value("${ibe.key.dist.batch_size:100}")
    private int batchSize;

    @Autowired
    public IDRequestServiceImpl(IDRequestDAO idRequestRepo, DataSource dataSource) {
        this.idRequestRepo = requireNonNull(idRequestRepo);
        this.dataSource = requireNonNull(dataSource);
    }

    @Override
    public IDRequest getByOwner(User owner, String idString) {
        Optional<IDRequest> request = idRequestRepo.findByOwnerForID(requireNonNull(owner), requireNonNull(idString));
        if (request.isPresent())
            return request.get();
        return null;
    }

    @Override
    public List<IDRequest> listNewRequests(int amount) {
        return idRequestRepo.findAllByStatus(IBECSR.APPLICATION_NOT_VERIFIED, PageRequest.of(0, amount));
    }

    @Override
    public List<IDRequest> list(User owner, int page, int amount, int status) {
        if (status == IBECSR.APPLICATION_STATUS_ALL)
            return idRequestRepo.findAllByUser(owner, PageRequest.of(page, amount));
        return idRequestRepo.findAllByUserAndStatus(owner, status, PageRequest.of(page, amount));
    }

    @Override
    public List<IDRequest> listUnhandledRequests(int amount) {
        return idRequestRepo.findAllByStatus(IBECSR.APPLICATION_STARTED, PageRequest.of(0, amount));
    }

    @Override
    public void requestHandled(Map<String, Integer> results) {
        BatchSqlUpdate sql = new BatchSqlUpdate(dataSource, "update ibe_id_request set application_status=? where identity_string=? and application_status<2");
        sql.setBatchSize(batchSize > results.size() ? results.size() : batchSize);
        sql.declareParameter(new SqlParameter(Types.INTEGER));
        sql.declareParameter(new SqlParameter(Types.VARCHAR));
        for (String id : results.keySet()) {
            sql.update(results.get(id), id);
        }
        sql.flush();
    }

    @Override
    public long count(User owner) {
        return idRequestRepo.countByOwner(requireNonNull(owner));
    }

    @Override
    public int doesIdBelongToUser(String id, User user, String idPassword) {
        Optional<IDRequest> request = idRequestRepo.findByOwnerForID(user, id);
        if (request.isPresent() == false) {
            return 2;  // the id doesn't belong to that user
        }
        String hash = request.get().getPassword();
        String exptHash = Hex.hex(Hash.sha512(idPassword));
        if (!exptHash.equalsIgnoreCase(hash))
            return 1;  // incorrect password
        return 0;
    }

    @Override
    public int doesIdRequestExist(String id) {
        IDRequest e = new IDRequest();
        e.setIdentityString(id);
        e.setStatus(IBECSR.APPLICATION_NOT_VERIFIED);
        Optional<IDRequest> request = idRequestRepo.findOne(Example.of(e));
        if (request.isPresent()) {
            return request.get().getStatus() + 1;
        }
        return 0;
    }

    @Override
    public void save(IDRequest request) {
        idRequestRepo.save(request);
    }

    @Override
    public IDRequest getByIDString(String idString) {
        Optional<IDRequest> request = idRequestRepo.findByIDString(idString);
        if (request.isPresent())
            return request.get();
        return null;
    }
}

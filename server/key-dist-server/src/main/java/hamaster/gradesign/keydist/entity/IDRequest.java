package hamaster.gradesign.keydist.entity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.fasterxml.jackson.annotation.JsonIgnore;

import hamaster.gradesgin.ibe.IBEConstraints;
import hamaster.gradesgin.ibe.io.SecureByteArrayOutputStream;
import hamaster.gradesgin.util.Hex;

/**
 * 保存用户身份描述信息申请的实体类
 * @author <a href="mailto:wangyeee@gmail.com">Wang Ye</a>
 */
@Entity
@Table(name = "IBE_ID_REQUEST")
public class IDRequest implements Serializable, Cloneable {
    private static final long serialVersionUID = -811075818715325346L;

    /**
     * 身份描述申请序号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "REQUEST_ID")
    private Integer requestId;

    /**
     * 申请者
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "APPLICANT")
    private User applicant;

    /**
     * 身份字符串
     */
    @Column(nullable = false, name = "IDENTITY_STRING")
    private String identityString;

    /**
     * The SHA512 hash of the password used to encrypt identity description.
     * Its plain text form will be encrypted with key generation server
     * public key and stored to passwordToKeyGen.
     */
    @Column(nullable = false, name = "PASSWORD")
    private String password;

    @Lob
    @Column(nullable = false, name = "PASSWORD_KEYGEN")
    private byte[] passwordToKeyGen;

    /**
     * 所使用的系统
     */
    @Column(nullable = false, name = "IBE_SYSTEM_ID")
    private Integer ibeSystemId;

    /**
     * 申请日期
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false, name = "APPLICATION_DATE")
    private Date applicationDate;

    /**
     * 申请状态
     */
    @Column(nullable = false, name = "APPLICATION_STATUS")
    private Integer status;

    public IDRequest() {
    }

    /**
     * 返回字节形式表示的部分<code>IDRequest</code>内容
     * @return 包含内容：<ul><li>
     * 请求编号 4字节</li><li>
     * 身份字符串长度 1字节</li><li>
     * 身份字符串内容 最多255字节 UTF-8编码</li><li>
     * 所属IBE系统编号 4字节</li><li>
     * 申请日期 8字节</li><li>
     * 当前状态 1字节</li></ul>
     */
    public byte[] toBytes() {
        ByteArrayOutputStream out = new SecureByteArrayOutputStream();
        byte[] ret = null;
        try {
            out.write(Hex.intToByte(requestId));
            byte[] id = identityString.getBytes(IBEConstraints.USER_STRING_ENCODING);
            out.write(id.length);
            out.write(id);
            out.write(Hex.intToByte(ibeSystemId));
            out.write(Hex.longToBytes(applicationDate.getTime()));
            out.write(status);
            out.flush();
            ret = out.toByteArray();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            out.reset();
        }
        return ret;
    }

    public Integer getRequestId() {
        return requestId;
    }

    public void setRequestId(Integer requestId) {
        this.requestId = requestId;
    }

    @JsonIgnore
    public User getApplicant() {
        return applicant;
    }

    /**
     * for generate json use only
     * @return applicant.getEmail()
     */
    public String getOwner() {
        return applicant == null ? null : applicant.getEmail();
    }

    public void setApplicant(User applicant) {
        this.applicant = applicant;
    }

    public String getIdentityString() {
        return identityString;
    }

    public void setIdentityString(String identityString) {
        this.identityString = identityString;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getIbeSystemId() {
        return ibeSystemId;
    }

    public void setIbeSystemId(Integer ibeSystemId) {
        this.ibeSystemId = ibeSystemId;
    }

    public Date getApplicationDate() {
        return applicationDate;
    }

    public void setApplicationDate(Date applicationDate) {
        this.applicationDate = applicationDate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public void setPasswordToKeyGen(byte[] passwordToKeyGen) {
        this.passwordToKeyGen = passwordToKeyGen;
    }

    @JsonIgnore
    public byte[] getPasswordToKeyGen() {
        return this.passwordToKeyGen;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        IDRequest copy = new IDRequest();
        copy.requestId = this.requestId;
        copy.applicant = this.applicant;
        copy.identityString = this.identityString;
        copy.password = this.password;
        copy.ibeSystemId = this.ibeSystemId;
        copy.applicationDate = this.applicationDate;
        copy.status = this.status;
        copy.passwordToKeyGen = this.passwordToKeyGen;
        return copy;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((applicant == null) ? 0 : applicant.hashCode());
        result = prime * result + ((applicationDate == null) ? 0 : applicationDate.hashCode());
        result = prime * result + ((ibeSystemId == null) ? 0 : ibeSystemId.hashCode());
        result = prime * result + ((identityString == null) ? 0 : identityString.hashCode());
        result = prime * result + ((password == null) ? 0 : password.hashCode());
        result = prime * result + Arrays.hashCode(passwordToKeyGen);
        result = prime * result + ((requestId == null) ? 0 : requestId.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IDRequest other = (IDRequest) obj;
        if (applicant == null) {
            if (other.applicant != null)
                return false;
        } else if (!applicant.equals(other.applicant))
            return false;
        if (applicationDate == null) {
            if (other.applicationDate != null)
                return false;
        } else if (!applicationDate.equals(other.applicationDate))
            return false;
        if (ibeSystemId == null) {
            if (other.ibeSystemId != null)
                return false;
        } else if (!ibeSystemId.equals(other.ibeSystemId))
            return false;
        if (identityString == null) {
            if (other.identityString != null)
                return false;
        } else if (!identityString.equals(other.identityString))
            return false;
        if (password == null) {
            if (other.password != null)
                return false;
        } else if (!password.equals(other.password))
            return false;
        if (!Arrays.equals(passwordToKeyGen, other.passwordToKeyGen))
            return false;
        if (requestId == null) {
            if (other.requestId != null)
                return false;
        } else if (!requestId.equals(other.requestId))
            return false;
        if (status == null) {
            if (other.status != null)
                return false;
        } else if (!status.equals(other.status))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "IDRequest [requestId=" + requestId + ", applicant=" + applicant + ", identityString=" + identityString
                + ", password=" + password + ", passwordToKeyGen=" + Arrays.toString(passwordToKeyGen)
                + ", ibeSystemId=" + ibeSystemId + ", applicationDate=" + applicationDate + ", status=" + status + "]";
    }
}

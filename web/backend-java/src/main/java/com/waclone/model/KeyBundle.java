package com.waclone.model;

import jakarta.persistence.*;

@Entity
@Table(name = "key_bundle")
public class KeyBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bundleId;

    @OneToOne(optional = false)
    @JoinColumn(name = "userId", unique = true,
                foreignKey = @ForeignKey(name = "fk_key_bundle_user",
                        foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE CASCADE"))
    private User user;

    @Column(length = 400) private String identityKey;
    @Column(length = 400) private String signingKey;
    @Column(length = 400) private String signedPreKey;
    @Column(length = 400) private String signedPreKeySig;

    @Lob
    @Column(length = 8000)
    private String oneTimePreKeys;

    protected KeyBundle() {}

    public KeyBundle(User user) { this.user = user; }

    public Long getBundleId() { return bundleId; }
    public User getUser() { return user; }
    public String getIdentityKey() { return identityKey; }
    public void setIdentityKey(String k) { this.identityKey = k; }
    public String getSigningKey() { return signingKey; }
    public void setSigningKey(String k) { this.signingKey = k; }
    public String getSignedPreKey() { return signedPreKey; }
    public void setSignedPreKey(String k) { this.signedPreKey = k; }
    public String getSignedPreKeySig() { return signedPreKeySig; }
    public void setSignedPreKeySig(String s) { this.signedPreKeySig = s; }
    public String getOneTimePreKeys() { return oneTimePreKeys; }
    public void setOneTimePreKeys(String j) { this.oneTimePreKeys = j; }
}

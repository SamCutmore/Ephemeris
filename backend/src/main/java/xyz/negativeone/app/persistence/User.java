package xyz.negativeone.app.persistence;

/** Ported from SPRE uk.ac.gla.spre.esclient.persistence.User (JPA → plain POJO for JDBC). */
public class User {
  private Integer id;
  private String name;
  private String betterPassword;

  public User() {}

  public User(String name, String password) {
    this.name = name;
    this.betterPassword = password;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Integer getId() {
    return id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getPassword() {
    return betterPassword;
  }

  public void setPassword(String password) {
    this.betterPassword = password;
  }
}

package minijava;

/** Basic error class in this project. */
public class MJError extends Error {

  public MJError(Exception wrapped) {
    super(wrapped);
  }

  public MJError(String message) {
    super(message);
  }
}

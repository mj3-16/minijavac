package minijava;

/** Basic error class in this project. */
public class MJError extends RuntimeException {

  public MJError(Exception wrapped) {
    super(wrapped);
  }

  public MJError(String message) {
    super(message);
  }
}

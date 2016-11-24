package minijava.firm;

import firm.Firm;

/**
 * Ugly workaround for static global state in {@link Firm}.
 *
 * <p><em>NEVER</em> call {@link Firm#init()} or {@link Firm#finish()}! Referencing {@link InitFirm}
 * (e.g. by calling the no-op method {@link InitFirm#init()}) will trigger a call to {@link
 * Firm#init()} <em>once</em> when this class is loaded by the jvm. The price we have to pay for
 * this is that the resources acquired by {@link Firm#init()} will not be freed until the jvm exits.
 * Yes, this <em>is</em> ugly! But there is nothing we can do about it (except for fixing jFirm).
 */
public class InitFirm {
  static {
    Firm.init();
  }

  private InitFirm() {}

  /** Does nothing, but can be used to trigger the static initializer */
  static void init() {}
}

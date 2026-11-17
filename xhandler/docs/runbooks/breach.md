# Suspected personal-data breach

The tenant is the controller; the platform is the processor (DESIGN.md §16). Ley 29733 and
DS 016-2024-JUS: the controller notifies the ANPD within 48 hours; we tell tenants at once.

1. **Contain** (minutes): revoke the leaked credentials or tokens, block the access path, keep
   the evidence (logs, `audit_log`, platform audit log). Don't delete anything.
2. **Assess** (hours): which tenants, which data (buyers' names and document numbers, documents,
   SOL credentials?), how many people, since when. Secrets are envelope-encrypted and keys live in
   the HSM: a database dump alone doesn't expose them.
3. **Tell the tenants immediately** (email to their notification address + in-app), with what
   happened, what data, what we did, and a contact. Give each the facts it needs for its 48-hour
   notice to the ANPD (and the CNSD for security incidents).
4. **Credentials exposed**: SOL users and API clients must be changed by the tenants in SUNAT;
   rotate the master key (`xhandler-admin rotate-keys`) if the key-encryption material may be
   involved.
5. **Record** the incident, timeline and decisions (ISO 27001 incident register) and review it.

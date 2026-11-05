# Cover note — to Sambat / LOS (TurnKey) integration team

*(Ready to copy into an email. Attach `LOS_Integration_API_Spec.docx` and `LOS_Appendix2_Review_and_Gaps.docx`.)*

---

**Subject:** Kjey PAPA ⇄ Sambat LOS (Payday Loan) — integration spec for review & items to confirm

Dear [Name / Sambat LOS team],

Thank you for sharing **Appendix 2 – New Loan Application API**. We have reviewed it in detail and mapped it against the data the Kjey PAPA app captures. Our side already implements the PDL application lifecycle (capture, submit, decision handling, repayment view) end-to-end against a mock, so adopting your real contract is largely a mapping exercise on our end.

We confirm our understanding that **LOS is hosted on the same Tricube server** as the Sambat Finance core-banking API, so our outbound calls will **reuse the existing SBF/Tricube OAuth** (no separate host or credentials). *Please confirm the LOS endpoints accept the same token/scopes as the SBF endpoints.*

**Attached:**
1. **LOS Integration API Specification** — the full integration we require, both directions (submit, product, reject, rework, approved, accept/reject, disbursement, repayment), with field-level schemas.
2. **Appendix 2 Review & Gap Analysis** — our field-by-field mapping of `new-loan-application` against the app's data, and the items still open.

**To move forward, could you please confirm / provide:**

1. **Notification mechanism — push or poll?** After we submit an application, how does Kjey PAPA receive the outcome (reject / rework / approved) and the later disbursement & repayment status? Does Tricube/LOS **push** notifications to our backend (webhooks), or should our backend **poll** Tricube for application status? If polling, please share the status/read endpoint(s) and a recommended cadence.

2. **The remaining API specifications / appendices**, to match Appendix 2:
   - Loan **product** configuration (catalog / amounts / tenors / rates / fees);
   - **Reject** notification;
   - **Rework** status;
   - **Approved** notification, including the generated **loan application form, loan contract, and repayment schedule**;
   - Customer **Accept / Reject** decision (how we relay the customer's signed e-contract);
   - **Disbursement** status and ongoing **repayment / loan** status updates.

3. **UAT / sandbox access + a test applicant** for an end-to-end dry run, and the **production** Tricube host/port for go-live.

*(Separately, we will align with you on the CBC standard code lists used by the coded fields — this is not required for this round.)*

We're happy to set up a short call to walk through the attached spec. Thank you again for your support.

Best regards,
[Your name]
Kjey PAPA / ezetik

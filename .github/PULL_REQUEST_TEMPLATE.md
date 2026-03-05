## Description

### Why is this change being made?

1. Standardize the PR process by adding reviewee and reviewer checklists that ensure consistent validation steps, improve code quality, and make reviews more efficient.


### What is changing?

1. Setting up change review template for the repo.


### Related Links
- **Issue #, if available**: N/A

---

## Testing

### How was this tested?

1. Validated the MD preview. The template needs to be merged to the main branch to be auto applied.

### When testing locally, provide testing artifact(s):

1. N/A

---

## Reviewee Checklist

**Update the checklist after submitting the PR**

- [ ] I have reviewed, tested and understand all changes
  *If not, why:*
- [ ] I have filled out the Description and Testing sections above
  *If not, why:*
- [ ] Build and Unit tests are passing
  *If not, why:* Just an MD update. No code changes.
- [ ] Unit test coverage check is passing
  *If not, why:* Just an MD update. No code changes.
- [ ] I have ensured no sensitive information is leaking (i.e., no logging of sensitive fields, or otherwise)
  *If not, why:*
- [ ] I have added explanatory comments for complex logic, new classes/methods and new tests
  *If not, why:* Just an MD update. No code changes.
- [ ] I have updated README/documentation (if needed)
  *If not, why:* No relevant doc changes required.
- [ ] I have clearly called out breaking changes (if any)
  *If not, why:* Just an MD update. No code changes.

---

## Reviewer Checklist

**All reviewers please ensure the following are true before reviewing:**

- Reviewee checklist has been accurately filled out
- Code changes align with stated purpose in description
- Test coverage adequately validates the changes

---

By submitting this pull request, I confirm that my contribution is made under the terms of the Apache 2.0 license.

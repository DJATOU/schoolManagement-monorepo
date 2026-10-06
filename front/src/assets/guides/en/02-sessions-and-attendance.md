# Sessions and attendance

## The series, the billing unit

A group's sessions are arranged in **series**. A series holds the number of sessions planned for the group, for example 4. The series is what gets paid, and what a student's balance is calculated on.

You never create series: when the current series is full, the next session opens a new one by itself. Its name follows the pattern **Group - MM-YYYY-NNN**.

> [!TIP]
> "Anglais 1 AS - 10-2026-001": first series of the Anglais 1 AS group, started in October 2026.

> [!NOTE]
> A month can hold two or three series of the same group: a series is not a month.

<!-- page -->

## Creating a session

Menu **Academic Tracking › Sessions**.

1. **Session details**: a title and the **Session type** (Course, Exercises, Exam, Revision, Other).
2. **Schedule**: the **Start date** and **Start time**. The end, two hours later, is filled in for you.
3. **Identifiers**: the **Group**, **Room** and **Teacher**. The teacher is pre-filled from the group.
4. Click **Save session**.

The session joins the group's current series.

<!-- page -->

## Repeating a session

**Recurrence** tab of the same form:

1. Tick **Repeat this session**.
2. Choose the **Days of the week**.
3. Set **Repeat until** (date included).
4. Click **Compute sessions**: the screen tells you how many will be created.
5. Click **Save session**.

> [!WARNING]
> If the room or the teacher is already taken on one slot, the whole recurrence is refused. Tick **Skip slots that are already taken** to create the other sessions and leave out only those.

<!-- page -->

## The calendar

Menu **Academic Tracking › Calendar**. **Month**, **Week**, **Day** and **List** views; **Today** goes back to the current date.

Click a session to open its window:

- **Session details** tab: group, room, teacher, date, time;
- **Attendances** tab: the roll call.

At the bottom: **Edit session**, **Validate session**, **Print attendance**, **Delete**, **Close**.

<!-- page -->

## Taking the roll call

In the **Attendances** tab, each student enrolled on the day of the session has a line:

- box ticked: **present**; box cleared: **absent**;
- **Justified**: for an excused absence;
- **Add a note**: free text.

**Check all** marks the whole class present. The **+** button adds a student from another group who comes to catch up.

Finally click **Validate session**. A window offers to **Print PDF** of the attendance sheet.

<!-- page -->

## Validating: not before the start time

A session can be validated **from its start time**. Before that, the button is greyed out and the screen explains why:

> "This session has not started yet: it can be validated from 12/10/2026 at 18:00."

A validated session counts: its attendance feeds what students owe, and its series can be paid to the teacher. Validating it early would distort those figures.

> [!WARNING]
> An absence recorded for a student who was not enrolled that day blocks the validation, and the screen names those lines. Click **Remove these lines**, then validate again.

<!-- page -->

## Correcting a validated session

Once the session is validated, the sheet can no longer be ticked: each line is corrected on its own, with the **Correct** button (pencil icon):

- present: **Mark absent**, **Mark absent (justified)**, **Remove the line**;
- absent: **Mark present**, **Edit the justification…**, **Remove the line**;
- student without a line: **Add: present**, **absent** or **absent (justified)**.

Each correction asks for a **Reason**, then **See preview** (what will change: cost, amount due, status), then **Confirm**. It is recorded in the correction log.

<!-- page -->

## Unvalidating a session

**Unvalidate session** removes all its attendance lines: it is to be validated again. Choose the reason, read the preview, then confirm.

A validated session cannot be deleted: unvalidate it first, then click **Delete**.

> [!WARNING]
> If its series has already been paid to the teacher, the session cannot be unvalidated: cancel the payout first (see [Teacher payroll](#paie-enseignants)).

<!-- page -->

## Justified absence: no effect on money

The justification is used to follow the student and for the right to a catch-up. It **changes no amount**:

- **no absence raises the amount due so far**, justified or not: only attended sessions do;
- **every absence after enrolment still counts in the cost of the series**: the seat was reserved.

To change a justification after validation: **Correct › Edit the justification…**. The history keeps who changed what, and when.

<!-- page -->

## Catch-ups

A student who missed a session can catch up in another group:

- menu **Academic Tracking › Catch-ups**, then **New catch-up request**;
- or the **+** button on the sheet of the host session.

The application decides the billing by itself:

- the student has a seat in another group of the **same level and same subject**: the catch-up is "**To be decided**";
- otherwise: "**Billed here**"; the host group bills it as for a member.

<!-- page -->

## Catch-ups to decide

Menu **Academic Tracking › Catch-ups to decide**. On each line, click **Decide**:

1. **Which session is being caught up?** Choose the missed session.
2. **Was that session already paid?** **Already paid — do not re-bill**, or **To be billed**.
3. Click **Save the decision**.

No answer is preselected: the choice is yours. Until then, the catch-up bills nothing and is not a debt.

> [!TIP]
> Go through this list every week: an attended session that nobody bills is an oversight.

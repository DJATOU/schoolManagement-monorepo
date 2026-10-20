# Getting started

## Signing in

1. Enter your **Username** and **Password**.
2. Click **Sign in**. The **Dashboard** opens.

To leave: click your name at the top right, then **Logout**.

## Two roles

| Role | What it can do |
|---|---|
| **Administrator** | everything: register, collect payments, validate, correct, pay teachers |
| **Viewer** | see everything, change nothing |

> [!NOTE]
> For a **Viewer** account, buttons that change data are greyed out: « Action réservée aux administrateurs ». **Revenue** and **Teacher payroll** are not offered.

<!-- page -->

## The top bar

- **☰** opens or closes the left menu.
- The **logo** takes you back to the dashboard.
- **Students**, **Groups**, **Teachers**: choose what you are looking for, then type at least two letters in the search box.
- The **book** icon opens this guide.
- **School year**: the year shown everywhere.
- **Your name**: light or dark mode, language (**Français**, **English**, **العربية**), **Account management**, **Logout**.

> [!TIP]
> To open Lucas Lefebvre's profile: **Students** tab, type "Luc", then pick "Lucas Lefebvre" in the suggestions.

<!-- page -->

## The left menu

Four sections. Only one stays open at a time.

- **Registration**: Students, Teachers, Groups, CSV Import.
- **Financial Management**: Payments, Discounts, Revenue, Teacher payroll.
- **Academic Tracking**: Sessions, Calendar, Catch-ups, Catch-ups to decide, Year-End / Promotion.
- **Academic Resources**: Levels, Rooms, Subjects, Group Types, Pricing.

> [!NOTE]
> The next chapters follow this menu: [Sessions and attendance](#seances), [Payments](#paiements), [Teacher payroll](#paie-enseignants).

<!-- page -->

## The school year

The **School year** selector chooses the year you are looking at. The ongoing year is marked **(current)**.

A **past year** opens **read-only**: a banner says so, and nothing can be changed. Everything stays visible: students, sessions, payments, receipts.

> [!WARNING]
> Before registering, collecting a payment or validating, check that the **current** year is selected. Otherwise the buttons stay greyed out.

<!-- page -->

## The dashboard

It opens after you sign in. The cards show the figures for the chosen year:

- **Active students** and **Teachers**;
- **Active groups**: those with students, "out of N created";
- **New enrollments**, **Leaving (all time)**, **Catch-ups**.

Below: **Sessions**, **Attendance** and the **Gender breakdown**.

Choose the period: **Year**, **Month** or **Range**. **Print** produces a PDF.

> [!NOTE]
> **Year** covers the school year, summer registrations included, not the calendar year.

<!-- page -->

## Setting up the school

To get going quickly, load your lists with **Registration › CSV Import**, in this order:

1. Levels, Subjects, Rooms, Group types
2. Prices (the price of one session)
3. Teachers, then Students (optional dateOfBirth, YYYY-MM-DD, column as **Text** in Excel)
4. Groups: they name a level, a subject, a teacher and a price already imported
5. Enrolments: one student, one group and an arrival date per row

Each file starts with a header row. The screen then shows how many rows were imported, how many were refused, and why.

> [!NOTE]
> A teacher who already exists is refused, so no duplicate is created. Two students may share a name: they are accepted.

<!-- page -->

## Importing enrolments

The enrolments file places students in their groups, like **Add a student** on the group's page, with the same checks: level, school year, enrolment already open.

- One row per enrolment: a student attending two groups has two rows.
- The group is looked up by name, in the **current** year.
- The **arrival date** is required (YYYY-MM-DD). Sessions from that day on are owed; earlier ones do not concern the student.
- Two students with the same name: add their phone number (phoneNumber column) to pick the right one. A phone number that does not match the student found gives a warning: check it is the right student.

> [!NOTE]
> The import only creates enrolments: no attendance, absence or payment.

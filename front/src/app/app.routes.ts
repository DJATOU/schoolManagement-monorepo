import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { LevelFormComponent } from './components/level/level-form/level-form.component';
import { RoomFormComponent } from './components/room/room-form/room-form.component';
import { PricingFormComponent } from './components/pricing/pricing-form/pricing-form.component';
import { StudentFormComponent } from './components/student/student-form/student-form.component';
import { StudentSearchComponent } from './components/student/student-search/student-search.component';
import { SubjectFormComponent } from './components/subject/subject-form/subject-form.component';
import { TeacherFormComponent } from './components/teacher/teacher-form/teacher-form.component';
import { GroupFormComponent } from './components/group/group-form/group-form.component';
import { GroupTypeFormComponent } from './components/groupType/group-type-form/group-type-form.component';
import { SessionFormComponent } from './components/session/session-form/session-form.component';
import { CalendarComponent } from './components/session/calendar/calendar.component';
import { StudentProfileComponent } from './components/student/student-profile/student-profile.component';
import { TeacherProfileComponent } from './components/teacher/teacher-profile/teacher-profile.component';
import { TeacherSearchComponent } from './components/teacher/teacher-search/teacher-search.component';
import { GroupSearchComponent } from './components/group/group-search/group-search.component';
import { LevelTableComponent } from './components/level/level-table/level-table.component';
import { RoomTableComponent } from './components/room/room-table/room-table.component';
import { SubjectTableComponent } from './components/subject/subject-table/subject-table.component';
import { GroupTypeTableComponent } from './components/groupType/group-type-table/group-type-table.component';
import { PricingTableComponent } from './components/pricing/pricing-table/pricing-table.component';
import { GroupProfileComponent } from './components/group/group-profile/group-profile.component';
import { CatchUpListComponent } from './components/catch-up/catch-up-list/catch-up-list.component';
import { DiscountListComponent } from './components/discount/discount-list/discount-list.component';
import { PaymentManagementComponent } from './components/admin/payment-management/payment-management.component';
import { RevenueReportComponent } from './components/admin/revenue/revenue-report.component';
import { SeriesDetailComponent } from './components/serie/series-detail/series-detail.component';
import { YearEndWorkflowComponent } from './components/year-end-workflow/year-end-workflow.component';
import { ImportComponent } from './components/import/import.component';
import { LoginComponent } from './components/auth/login/login.component';
import { UserManagementComponent } from './components/auth/user-management/user-management.component';
import { authGuard, roleGuard } from './services/auth.guard';

export const routes: Routes = [
  // Point d'accès public : écran de connexion (hors shell).
  { path: 'login', component: LoginComponent },

  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'subscription', component: StudentFormComponent, canActivate: [authGuard] },
  { path: 'student', component: StudentSearchComponent, canActivate: [authGuard] },
  { path: 'teacher', component: TeacherSearchComponent, canActivate: [authGuard] },
  { path: 'group', component: GroupSearchComponent, canActivate: [authGuard] },
  { path: 'level/new', component: LevelFormComponent, canActivate: [authGuard] },
  { path: 'level/edit/:id', component: LevelFormComponent, canActivate: [authGuard] },
  { path: 'room/new', component: RoomFormComponent, canActivate: [authGuard] },
  { path: 'room/edit/:id', component: RoomFormComponent, canActivate: [authGuard] },
  { path: 'pricing/new', component: PricingFormComponent, canActivate: [authGuard] },
  { path: 'pricing/edit/:id', component: PricingFormComponent, canActivate: [authGuard] },
  { path: 'subject/new', component: SubjectFormComponent, canActivate: [authGuard] },
  { path: 'subject/edit/:id', component: SubjectFormComponent, canActivate: [authGuard] },
  { path: 'teacher/new', component: TeacherFormComponent, canActivate: [authGuard] },
  { path: 'teacher/edit/:id', component: TeacherFormComponent, canActivate: [authGuard] },
  { path: 'group/new', component: GroupFormComponent, canActivate: [authGuard] },
  { path: 'group/edit/:id', component: GroupFormComponent, canActivate: [authGuard] },
  { path: 'groupType/new', component: GroupTypeFormComponent, canActivate: [authGuard] },
  { path: 'groupType/edit/:id', component: GroupTypeFormComponent, canActivate: [authGuard] },
  { path: 'session/new', component: SessionFormComponent, canActivate: [authGuard] },
  { path: 'calendar/new', component: CalendarComponent, canActivate: [authGuard] },
  { path: 'level/table', component: LevelTableComponent, canActivate: [authGuard] },
  { path: 'room/table', component: RoomTableComponent, canActivate: [authGuard] },
  { path: 'subject/table', component: SubjectTableComponent, canActivate: [authGuard] },
  { path: 'groupType/table', component: GroupTypeTableComponent, canActivate: [authGuard] },
  { path: 'pricing/table', component: PricingTableComponent, canActivate: [authGuard] },
  { path: 'student/:id', component: StudentProfileComponent, canActivate: [authGuard] },
  { path: 'teacher/:id', component: TeacherProfileComponent, canActivate: [authGuard] },
  { path: 'group/:groupId/series/:seriesId', component: SeriesDetailComponent, canActivate: [authGuard] },
  { path: 'group/:id', component: GroupProfileComponent, canActivate: [authGuard] },
  { path: 'catch-ups', component: CatchUpListComponent, canActivate: [authGuard] },
  { path: 'discounts', component: DiscountListComponent, canActivate: [authGuard] },
  { path: 'admin/payment-management', component: PaymentManagementComponent, canActivate: [roleGuard('ADMIN')] },
  // Recettes : données financières, ADMIN uniquement (l'API l'exige aussi).
  { path: 'admin/revenue', component: RevenueReportComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'admin/users', component: UserManagementComponent, canActivate: [roleGuard('ADMIN')] },
  { path: 'year-end', component: YearEndWorkflowComponent, canActivate: [authGuard] },
  { path: 'import', component: ImportComponent, canActivate: [authGuard] },

  // Toute route inconnue → login (le guard redirigera vers dashboard si déjà connecté).
  { path: '**', redirectTo: '/login' },
];
  
  @NgModule({
    imports: [RouterModule.forRoot(routes, { enableTracing: true })],
    exports: [RouterModule]
  })
  export class AppRoutingModule { }
  

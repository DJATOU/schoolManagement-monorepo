import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { UserAccount } from '../../../models/auth/auth.model';
import { UserService } from '../../../services/user.service';

/**
 * Vue de gestion des comptes utilisateurs (réservée à l'ADMIN).
 * Création, liste, activation/désactivation, réinitialisation de mot de passe.
 */
@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatSlideToggleModule,
    MatTooltipModule,
    MatSnackBarModule,
    TranslateModule
  ],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss']
})
export class UserManagementComponent implements OnInit {
  form!: FormGroup;
  users: UserAccount[] = [];
  loading = false;
  hidePassword = true;
  displayedColumns = ['username', 'role', 'status', 'actions'];

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      username: ['', Validators.required],
      password: ['', [Validators.required, Validators.minLength(6)]],
      role: ['VIEWER', Validators.required]
    });
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.userService.getUsers().subscribe({
      next: users => { this.users = users; this.loading = false; },
      error: err => { this.notify(err.message); this.loading = false; }
    });
  }

  createUser(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.userService.createUser(this.form.value).subscribe({
      next: () => {
        this.notify(this.translate.instant('AUTH.USERS.CREATED'));
        this.form.reset({ role: 'VIEWER' });
        this.loadUsers();
      },
      error: err => this.notify(err.message)
    });
  }

  toggleEnabled(user: UserAccount): void {
    const action = user.enabled ? this.userService.disableUser(user.id) : this.userService.enableUser(user.id);
    action.subscribe({
      next: updated => {
        user.enabled = updated.enabled;
        this.notify(this.translate.instant(updated.enabled ? 'AUTH.USERS.ENABLED' : 'AUTH.USERS.DISABLED'));
      },
      error: err => this.notify(err.message)
    });
  }

  resetPassword(user: UserAccount): void {
    const pwd = window.prompt(this.translate.instant('AUTH.USERS.NEW_PASSWORD_PROMPT'));
    if (!pwd) {
      return;
    }
    this.userService.resetPassword(user.id, pwd).subscribe({
      next: () => this.notify(this.translate.instant('AUTH.USERS.PASSWORD_RESET')),
      error: err => this.notify(err.message)
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('COMMON.CANCEL'), { duration: 3500 });
  }
}

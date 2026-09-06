import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { TranslationService, TranslatePipe } from '../../core/i18n';
import { LanguageSwitcherComponent } from '../../shared/components/language-switcher/language-switcher.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslatePipe,
    LanguageSwitcherComponent
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  readonly authService = inject(AuthService);
  readonly translationService = inject(TranslationService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  isLoading = false;
  errorMessage = '';
  currentYear = new Date().getFullYear();

  onSubmit(): void {
    if (!this.username || !this.password) {
      this.errorMessage = this.translationService.currentLang() === 'ar'
        ? 'يرجى إدخال اسم المستخدم وكلمة المرور'
        : 'Please enter your username and password';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login({ username: this.username, password: this.password })
      .subscribe({
        next: (response) => {
          this.isLoading = false;
          if (response.success) {
            if (this.authService.isPlatformAdmin()) {
              this.router.navigate(['/platform/dashboard']);
            } else {
              this.router.navigate(['/dashboard']);
            }
          } else {
            this.errorMessage = response.message || this.translationService.translate('AUTH.LOGIN_ERROR');
          }
        },
        error: (err) => {
          this.isLoading = false;
          this.errorMessage = err.error?.message || this.translationService.translate('AUTH.LOGIN_ERROR');
        }
      });
  }
}

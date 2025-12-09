# 🚀 Quick Start Guide

**Date**: 2025-12-04
**Status**: ✅ Production Ready

---

## ⚡ Démarrage Rapide (5 minutes)

### 1. Backend (Spring Boot)

```bash
cd /Users/tayebdj/IdeaProjects/schoolManagement

# Démarrer le backend
./mvnw spring-boot:run

# Vérifier
curl http://localhost:8080/api/payments?page=0&size=20
```

**Devrait retourner**: JSON avec `{content: [...], metadata: {...}}`

---

### 2. Frontend (Angular)

```bash
cd /Users/tayebdj/IdeaProjects/schoolManagement-Font

# Installer les dépendances (si pas fait)
npm install

# Démarrer le frontend
ng serve

# Ouvrir le navigateur
open http://localhost:4200
```

---

## 📊 Vérifier Que Tout Fonctionne

### Backend - Endpoints Phase 2

```bash
# Tous les paiements (paginé)
curl "http://localhost:8080/api/payments?page=0&size=20"

# Paiements d'un étudiant (paginé)
curl "http://localhost:8080/api/payments/student/1?page=0&size=10"

# Traiter un paiement
curl -X POST http://localhost:8080/api/payments/process \
  -H "Content-Type: application/json" \
  -d '{"studentId":1,"groupId":1,"sessionSeriesId":1,"amountPaid":500.00}'

# Statut de paiement d'un groupe
curl "http://localhost:8080/api/payments/1/students-payment-status"

# Sessions impayées d'un étudiant
curl "http://localhost:8080/api/payments/students/1/unpaid-sessions"
```

### Frontend - Services Disponibles

**PaymentService** (synchronisé avec backend):
- `getAllPaymentsPaginated(page, size)` ✅
- `getPaymentsByStudentPaginated(studentId, page, size)` ✅
- `processPayment(payment)` ✅
- `getStudentsPaymentStatus(groupId)` ✅
- `getUnpaidSessions(studentId)` ✅
- `getStudentPaymentStatus(studentId)` ✅

---

## 📁 Documentation Complète

### Backend - Phase 2
- `PHASE2_COMPLETE.md` - ✅ Vue d'ensemble complète
- `BACKEND_FRONTEND_SYNC.md` - 🔗 Synchronisation API
- `CLEANUP_SUMMARY.md` - 🧹 Nettoyage backend

### Frontend
- `frontend/FRONTEND_CLEANUP_SUMMARY.md` - 🧹 Nettoyage frontend
- `FRONTEND_INTEGRATION_GUIDE.md` - 📖 Guide d'intégration

### Full-Stack
- `FULLSTACK_CLEANUP_COMPLETE.md` - 🎉 Récapitulatif complet
- `QUICK_START.md` - ⚡ Ce guide

---

## 🔧 Configuration Production

### Backend

**Fichier**: `src/main/resources/application.properties`

```properties
# Production database
spring.datasource.url=jdbc:postgresql://prod-db:5432/schooldb
spring.datasource.username=prod_user
spring.datasource.password=prod_password

# CORS pour frontend prod
spring.web.cors.allowed-origins=https://votre-domaine-frontend.com
```

### Frontend

**Fichier**: `frontend/src/environment.prod.ts`

```typescript
export const environment = {
    production: true,
    apiUrl: 'https://api.votre-domaine.com',  // ⚠️ À CONFIGURER
    imagesPath: '/personne/'
};
```

---

## 🐛 Problèmes Courants

### Backend ne démarre pas

**Erreur**: `Money.java NullPointerException`
**Solution**: ✅ Déjà corrigé (static initialization order)

**Erreur**: `Port 8080 already in use`
**Solution**:
```bash
# Trouver le processus
lsof -i :8080

# Tuer le processus
kill -9 <PID>
```

### Frontend ne se connecte pas au backend

**Erreur**: CORS Error
**Solution**: Vérifier CORS dans `application.properties`:
```properties
spring.web.cors.allowed-origins=http://localhost:4200
```

**Erreur**: 404 Not Found
**Solution**: Vérifier que le backend est démarré sur port 8080

---

## ✅ Checklist de Déploiement

### Avant de Déployer

- [ ] Backend compile sans erreurs (`./mvnw clean package`)
- [ ] Frontend compile sans erreurs (`ng build --configuration production`)
- [ ] `environment.prod.ts` configuré avec la bonne URL
- [ ] CORS backend configuré pour prod
- [ ] Base de données prod configurée
- [ ] SSL/HTTPS activé

### Après Déploiement

- [ ] Backend accessible (`curl https://api.votre-domaine.com/api/payments`)
- [ ] Frontend accessible (`https://votre-domaine.com`)
- [ ] Pagination fonctionne
- [ ] Aucune erreur CORS
- [ ] Logs propres (pas de console.log)

---

## 🎯 Next Steps

1. **Tester localement** ✅
   ```bash
   # Backend
   ./mvnw spring-boot:run

   # Frontend
   ng serve
   ```

2. **Build de production** ⚠️
   ```bash
   # Backend
   ./mvnw clean package

   # Frontend
   ng build --configuration production
   ```

3. **Déployer** 🚀
   - Configurer les URLs de production
   - Déployer backend (Heroku, AWS, etc.)
   - Déployer frontend (Netlify, Vercel, etc.)

---

## 💡 Aide Rapide

**Backend ne démarre pas?**
→ Voir `PHASE2_TEST_GUIDE.md`

**Frontend ne se connecte pas?**
→ Vérifier `environment.ts` et CORS

**Erreur de pagination?**
→ Voir `BACKEND_FRONTEND_SYNC.md`

**Besoin de plus de détails?**
→ Lire `FULLSTACK_CLEANUP_COMPLETE.md`

---

**Status**: ✅ **READY TO RUN**

🎉 **Tout est prêt! Démarrez et testez!** 🎉

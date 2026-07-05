# Indicateur de Statut de Paiement sur les Cards Étudiants

## 📋 Vue d'ensemble

Cette fonctionnalité ajoute un indicateur visuel de statut de paiement sur chaque card d'étudiant, permettant d'identifier rapidement les étudiants en retard de paiement.

## ✨ Fonctionnalités

### 1. Indicateur visuel (Mat-Chip)
- **Position**: En haut à droite de chaque card étudiant
- **Vert (✓ À jour)**: L'étudiant a payé toutes ses sessions validées
- **Rouge (⚠ En retard)**: L'étudiant a des paiements en retard (avec animation de pulsation)

### 2. Tooltip informatif
Au survol du chip rouge, un tooltip affiche:
- Liste des groupes où l'étudiant est en retard
- Nombre de sessions impayées par groupe
- Montant dû / montant payé (ex: "Reste 2000 DA (2000/4000 DA)")

### 3. Calcul intelligent
Le système prend en compte:
- ✅ Paiements partiels
- ✅ Sessions de rattrapage
- ✅ Statut de présence (seules les sessions où l'étudiant était présent comptent)
- ✅ Paiements actifs (les paiements définitivement supprimés sont exclus)

## 📁 Structure des fichiers

### Nouveaux fichiers créés

```
front/src/app/
├── models/
│   └── student-payment-status.ts          # Interfaces TypeScript
└── services/
    └── student-payment-status.service.ts  # Service de calcul
```

### Fichiers modifiés

```
front/src/app/components/
├── shared/profile-card/
│   ├── profile-card.component.ts          # Ajout Input paymentStatus
│   ├── profile-card.component.html        # Ajout chip indicateur
│   └── profile-card.component.scss        # Styles de l'indicateur
└── student/student-card/
    ├── student-card.component.ts          # Chargement du statut
    └── student-card.component.html        # Passage du statut
```

## 🔧 Interfaces TypeScript

### StudentPaymentStatus
```typescript
interface StudentPaymentStatus {
  studentId: number;
  paymentStatus: 'GOOD' | 'LATE';
  lateGroups: LateGroupDetails[];
  totalDue: number;
  totalPaid: number;
}
```

### LateGroupDetails
```typescript
interface LateGroupDetails {
  groupId: number;
  groupName: string;
  unpaidSessionsCount: number;
  dueAmount: number;
  paidAmount: number;
}
```

## 🎨 Rendu visuel

### Chip "À jour" (Vert)
```
┌──────────────────┐
│ ✓ À jour         │
└──────────────────┘
```
- Couleur: #4caf50 (vert Material)
- Icône: check_circle
- Pas de tooltip

### Chip "En retard" (Rouge avec pulsation)
```
┌──────────────────┐
│ ⚠ En retard      │ ← Animation pulse
└──────────────────┘

Tooltip au survol:
┌────────────────────────────────────┐
│ Paiements en retard:               │
│ • Groupe A: 2 session(s) -         │
│   Reste 4000.00 DA (0.00/4000.00)  │
│ • Groupe B: 1 session(s) -         │
│   Reste 2000.00 DA (0.00/2000.00)  │
└────────────────────────────────────┘
```
- Couleur: #f44336 (rouge Material)
- Icône: warning
- Animation: pulse-warning (2s)

## 🧮 Logique métier

### Règle "En retard"
Un étudiant est considéré en retard si:
```typescript
(sessions validées à payer) > 0
ET
(montant total dû) > (montant total payé)
```

### Sessions comptabilisées
Une session compte comme "à payer" si:
- L'étudiant était **présent** (`isPresent = true`)
- OU la session est configurée comme **payante même en absence**

### Paiements comptabilisés
Seuls les paiements **actifs** sont comptés:
- `active = true`
- `permanentlyDeleted = false`

## 📡 API Backend utilisée

### Endpoint
```
GET /api/payments/students/{studentId}/payment-status
```

### Réponse (GroupPaymentStatus[])
```json
[
  {
    "groupId": 1,
    "groupName": "Groupe A",
    "seriesStatuses": [
      {
        "seriesId": 1,
        "seriesName": "Série 1",
        "sessionStatuses": [
          {
            "sessionId": 1,
            "isPresent": true,
            "amountDue": 2000.0,
            "amountPaid": 0.0
          }
        ]
      }
    ]
  }
]
```

## 🚀 Utilisation

### Dans un composant
```typescript
import { StudentPaymentStatusService } from '@services/student-payment-status.service';

constructor(private paymentStatusService: StudentPaymentStatusService) {}

loadStatus(studentId: number) {
  this.paymentStatusService.getStudentPaymentStatus(studentId).subscribe(
    status => {
      console.log('Status:', status.paymentStatus); // 'GOOD' ou 'LATE'
      console.log('Late groups:', status.lateGroups);
    }
  );
}
```

### Chargement multiple (optimisé)
```typescript
const studentIds = [1, 2, 3, 4, 5];
this.paymentStatusService.getMultipleStudentsPaymentStatus(studentIds).subscribe(
  statusMap => {
    const status1 = statusMap.get(1); // StudentPaymentStatus pour étudiant 1
    const status2 = statusMap.get(2); // StudentPaymentStatus pour étudiant 2
  }
);
```

## 🎯 Performance

### Optimisations
- ✅ Chargement en parallèle avec `forkJoin`
- ✅ Gestion d'erreur gracieuse (fallback sur GOOD)
- ✅ Calculs côté TypeScript (pas dans le template)
- ✅ Tooltip généré une seule fois via méthode

### Recommandations
Pour de grandes listes (>20 étudiants), envisager:
1. **Pagination**: Charger les statuts par page
2. **Lazy loading**: Charger le statut au scroll
3. **Cache**: Mettre en cache les statuts pendant 1-2 minutes

## 🧪 Tests

### Test manuel
1. Créer un étudiant avec des sessions validées non payées
2. Vérifier que le chip rouge "En retard" apparaît
3. Survoler le chip → tooltip avec détails groupes
4. Payer une session → chip devrait passer au vert si tout est payé

### Cas limites
- ✅ Étudiant sans aucun paiement → GOOD (pas de session validée)
- ✅ Étudiant avec paiement partiel → LATE (montant dû > payé)
- ✅ Étudiant avec paiement supprimé → LATE (ne compte pas)
- ✅ Erreur réseau → Pas d'indicateur (fallback silencieux)

## 🔮 Évolutions futures

### Court terme
- [ ] Ajouter l'indicateur sur la vue table (student-list-item)
- [ ] Badge de notification dans le menu admin

### Moyen terme
- [ ] Filtrer la liste par statut (GOOD/LATE)
- [ ] Graphique de suivi des retards
- [ ] Notification par email aux étudiants en retard

### Long terme
- [ ] Prédiction des risques de retard (ML)
- [ ] Rappels automatiques par WhatsApp
- [ ] Tableau de bord administrateur

## 🐛 Dépannage

### L'indicateur ne s'affiche pas
1. Vérifier que `profileType === 'student'`
2. Vérifier que `paymentStatus` n'est pas `undefined`
3. Ouvrir la console: regarder les logs "Payment status loaded"

### Le tooltip ne s'affiche pas
1. Vérifier que `paymentStatus.paymentStatus === 'LATE'`
2. Vérifier que `lateGroups` n'est pas vide
3. Vérifier que MatTooltipModule est importé

### Erreur 404 sur l'API
1. Vérifier que le backend est démarré
2. Vérifier l'endpoint `/api/payments/students/{id}/payment-status`
3. Regarder les logs backend pour les erreurs

## 📚 Références

- [Angular Material Chips](https://material.angular.io/components/chips/overview)
- [Angular Material Tooltip](https://material.angular.io/components/tooltip/overview)
- [RxJS forkJoin](https://rxjs.dev/api/index/function/forkJoin)
- [Payment Service Backend](../../back/src/main/java/com/school/management/service/payment/)

---

**Auteur**: Claude Code
**Date**: 2025-12-17
**Version**: 1.0.0

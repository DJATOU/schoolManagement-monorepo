# Fix Problème de Compilation

## ❌ Problème

```
Error: Could not find or load main class com.school.management.SchoolManagementApplication
Caused by: java.lang.ClassNotFoundException
```

**Cause** : Bug entre maven-compiler-plugin et Java 21 avec Lombok/MapStruct

---

## ✅ Solution - Compiler avec IntelliJ IDEA

### Méthode 1: Recharger le Projet Maven

1. **Ouvrir IntelliJ IDEA**
2. Clic droit sur `back/pom.xml`
3. **Maven** → **Reload Project**
4. Attendre que IntelliJ télécharge toutes les dépendances

### Méthode 2: Build avec IntelliJ

1. Menu **Build** → **Rebuild Project**
2. Ou `Cmd+Shift+F9` (Mac) / `Ctrl+Shift+F9` (Windows)

### Méthode 3: Invalider les caches IntelliJ

1. Menu **File** → **Invalidate Caches / Restart**
2. Cocher **Invalidate and Restart**
3. IntelliJ va redémarrer et rebuild

---

## 🚀 Lancer l'Application

### Depuis IntelliJ (RECOMMANDÉ)

1. **Ouvrir** `back/src/main/java/com/school/management/SchoolManagementApplication.java`
2. Clic droit sur le fichier
3. **Run 'SchoolManagementApplication'**

Ou :

1. Chercher la classe main dans IntelliJ (Cmd+O / Ctrl+N)
2. Taper "SchoolManagement"
3. Cliquer sur la flèche verte ▶️ à côté de `public static void main`

### Avec le wrapper Maven (si ça ne marche pas)

Si IntelliJ compile correctement, vous pouvez lancer :

```bash
cd back
./mvnw spring-boot:run
```

---

## 🔧 Alternative: Utiliser Java 17

Si le problème persiste avec Java 21, vous pouvez downgrade vers Java 17 :

### 1. Changer dans pom.xml

```xml
<java.version>17</java.version>
```

### 2. Changer dans IntelliJ

1. **File** → **Project Structure** (Cmd+;)
2. **Project** → **SDK** → Choisir Java 17
3. **Modules** → **schoolManagement** → **Language level** → 17

### 3. Télécharger Java 17

Si vous ne l'avez pas :
```bash
brew install openjdk@17  # Mac
# ou télécharger depuis https://adoptium.net/
```

---

## 🎯 Vérifier que ça Marche

Une fois l'application démarrée, vous devriez voir :

```
Initializing roles...
Created role: ROLE_ADMIN
Created role: ROLE_TEACHER
Created role: ROLE_STUDENT
Created role: ROLE_PARENT
Roles initialized successfully

Started SchoolManagementApplication in X.XXX seconds
```

Puis testez :
```bash
curl http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin123!",
    "email": "admin@school.com",
    "firstName": "Super",
    "lastName": "Admin"
  }'
```

---

## 📝 Notes

- Le problème vient d'un bug entre maven-compiler-plugin 3.13.0 et Java 21
- J'ai déjà downgrade à version 3.11.0 dans le pom.xml
- IntelliJ utilise son propre compilateur qui fonctionne mieux avec Java 21
- Une fois compilé par IntelliJ, `./mvnw spring-boot:run` devrait fonctionner

---

## ⚠️ Si Rien ne Fonctionne

1. **Nettoyer complètement** :
```bash
cd back
rm -rf target
./mvnw clean
```

2. **Dans IntelliJ** :
   - Build → Clean Project
   - File → Invalidate Caches / Restart

3. **Vérifier la version de Java** :
```bash
java -version
# Devrait afficher temurin-21
```

4. **Dernier recours - Maven offline** :
```bash
./mvnw clean install -DskipTests -o
```

Si vraiment rien ne marche, passez à Java 17 temporairement.

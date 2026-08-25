# =====================================================================
# Frontend Angular + nginx — image de production
# Contexte de build attendu : ./front
#
# nginx joue deux rôles : il sert le build Angular en fichiers statiques et
# relaie /api et /personne vers le service Spring. Le front et l'API partagent
# ainsi une seule origine : pas de CORS, un seul port exposé.
# =====================================================================

# --------- Étape 1 : build Angular ---------
# Node 20 LTS : Angular 17.3 le prend en charge officiellement.
FROM node:20-alpine AS build

WORKDIR /build

# Dépendances d'abord : le cache Docker est conservé tant que les fichiers
# package*.json ne changent pas.
COPY package.json package-lock.json ./

# --legacy-peer-deps reprend ce qui était déjà nécessaire à ce projet : plusieurs
# dépendances (FullCalendar, pdfmake) déclarent des pairs incompatibles entre
# elles. npm ci garantit par ailleurs des versions identiques à celles du
# package-lock, donc un build reproductible.
RUN npm ci --legacy-peer-deps

COPY . .

# Le script "build" du package.json cible déjà --configuration=production, ce qui
# applique le remplacement de fichier vers environment.prod.ts (apiUrl vide).
RUN npm run build

# --------- Étape 2 : nginx ---------
FROM nginx:1.27-alpine

# Le constructeur "application" d'Angular écrit dans un sous-dossier browser/.
COPY --from=build /build/dist/school-management-front/browser /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

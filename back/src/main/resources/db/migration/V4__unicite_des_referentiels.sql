-- =====================================================================
-- Unicité du nom des référentiels : niveau, matière, salle, type de groupe
--
-- Ces quatre entités sont désignées PAR LEUR NOM dans les fichiers d'import
-- (un CSV d'élèves cite « 1er année », un CSV de groupes cite « Maths »).
-- Deux lignes homonymes rendent donc la résolution indécidable : l'import
-- d'élèves échouait sur « Query did not return a unique result: 2 results
-- were returned », un message qui ne nomme ni le doublon, ni l'entité, ni le
-- fichier fautif.
--
-- La garde applicative (CsvImportService) refuse désormais un nom déjà
-- présent. Elle ne suffit pas : entre son test et l'insertion, un second
-- import peut créer l'homonyme. L'unicité est donc portée ici, par le
-- STOCKAGE, seul endroit où elle vaut quel que soit le nombre d'instances.
--
-- ⚠ Cette migration échoue si des doublons existent déjà. C'est délibéré :
-- supprimer des lignes de référentiel automatiquement reviendrait à choisir
-- à la place de l'administrateur laquelle conserver, alors que les deux
-- peuvent être référencées par des élèves ou des groupes différents. Le
-- nettoyage est un acte manuel, et l'échec au démarrage en est le rappel.
-- =====================================================================

-- Index et non contrainte UNIQUE : un index partiel reste possible plus tard
-- (par exemple restreint aux lignes actives) sans avoir à reprendre le schéma.
CREATE UNIQUE INDEX uk_level_name ON level (name);
CREATE UNIQUE INDEX uk_subject_name ON subject (name);
CREATE UNIQUE INDEX uk_room_name ON room (name);
CREATE UNIQUE INDEX uk_group_types_name ON group_types (name);

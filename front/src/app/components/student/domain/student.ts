export interface Student {
    firstName: string;
    id?: number;
    lastName: string;
    gender: string;
    email: string;
    phoneNumber: string;
    dateOfBirth: Date;
    placeOfBirth: string;
    photo: string;

    // Champs portés par le backend (StudentDTO) et saisis à l'inscription : ils doivent
    // rester modifiables ensuite, sinon une erreur de saisie est définitive.
    nationality?: string;
    communicationPreference?: string;
    address?: string;
    city?: string;
    level: number;
    levelId: number;
    levelName?: string;
    groupIds?: number[];
    tutorId?: number;
    establishment: string;
    averageScore?: number;
    isPresent?: boolean;
    isJustified?: boolean;
    description?: string;
    isCatchUp ?: boolean;
    /**
     * État de facturation du rattrapage, relayé par le serveur pour la feuille de présence.
     * Pilote le badge « à préciser » : une séance consommée que personne ne facture encore doit
     * être visible, sans pour autant bloquer la validation de la séance.
     */
    catchUpBillingState?: 'PENDING' | 'RESOLVED' | 'HOST_BILLED';
    /** Statut d'inscription : ACTIVE (par défaut) ou INACTIVE (étudiant désactivé/parti). */
    status?: 'ACTIVE' | 'INACTIVE' | string;
    active?: boolean;
  }
  
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
    /** Statut d'inscription : ACTIVE (par défaut) ou INACTIVE (étudiant désactivé/parti). */
    status?: 'ACTIVE' | 'INACTIVE' | string;
    active?: boolean;
  }
  
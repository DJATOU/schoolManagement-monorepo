// models/response.ts
export interface ApiResponse {
    message: string;
  }
  
  /**
   * Refus d'une écriture, tel que le serveur le rédige. Une inscription refusée porte `message`
   * (année et bornes, départ en cause, niveau) ; une inscription en double porte
   * `alreadyAssociatedEntities`, les groupes ou élèves déjà inscrits ; un élément introuvable porte
   * `error`.
   */
  export interface ApiError {
    status: number;
    error: {
      alreadyAssociatedEntities?: string[];
      message?: string;
      error?: string;
    } | null;
  }
